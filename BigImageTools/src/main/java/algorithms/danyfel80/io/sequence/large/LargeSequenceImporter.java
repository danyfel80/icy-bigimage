/*
 * Copyright 2010-2018 Institut Pasteur.
 * 
 * This file is part of Icy.
 * 
 * Icy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Icy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Icy. If not, see <http://www.gnu.org/licenses/>.
 */
package algorithms.danyfel80.io.sequence.large;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import icy.common.exception.UnsupportedFormatException;
import icy.common.listener.DetailedProgressListener;
import icy.file.FileUtil;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.roi.ROI;
import icy.roi.ROIUtil;
import icy.sequence.MetaDataUtil;
import icy.sequence.Sequence;
import icy.type.DataType;
import icy.type.dimension.Dimension2D;
import icy.util.OMEUtil;
import icy.util.XMLUtil;
import ome.xml.meta.OMEXMLMetadata;
import plugins.kernel.importer.LociImporterPlugin;
import plugins.kernel.roi.roi2d.ROI2DShape;

/**
 * @author Daniel Felipe Gonzalez Obando
 *
 */
public class LargeSequenceImporter implements Callable<Sequence> {

	private Path filePath;
	private double targetResolution;
	private Rectangle2D targetPixelRectangle;
	private Set<DetailedProgressListener> progressListeners;

	private LociImporterPlugin importer;
	private Dimension targetTileSize;
	private Dimension targetImageSize;
	private OMEXMLMetadata fileMetadata;
	private int channelSize;
	private DataType dataType;
	private String targetImageName;
	private Dimension2D targetPixelSize;
	private Point2D targetPosition;
	private double scaleFactor;
	private Rectangle tileGridRectangle;
	private Dimension resultTileSize;
	private Dimension resultImageSize;

	private ThreadPoolExecutor threadPool;
	private LociImporterPlugin[] subImporterArray;
	private ArrayBlockingQueue<LociImporterPlugin> subImporters;
	private int numImporters;

	private IcyBufferedImage resultImage;
	private Sequence resultSequence;
	private Double targetRectanglePosition;

	public LargeSequenceImporter() {
		progressListeners = new HashSet<>();
	}

	public Path getFilePath() {
		return filePath;
	}

	public void setFilePath(Path filePath) {
		this.filePath = filePath;
	}

	public double getTargetResolution() {
		return targetResolution;
	}

	public void setTargetResolution(double targetResolution) {
		this.targetResolution = targetResolution;
	}

	public Rectangle2D getTargetRectangle() {
		return targetPixelRectangle;
	}

	public void setTargetPixelRectangle(Rectangle2D targetRectangle) {
		this.targetPixelRectangle = targetRectangle;
	}

	public void addProgressListener(DetailedProgressListener progressListener) {
		this.progressListeners.add(progressListener);
	}

	public void removeProgressListener(DetailedProgressListener progressListener) {
		this.progressListeners.remove(progressListener);
	}

	@Override
	public Sequence call() throws Exception {
		checkParameters();
		createLociImporter();
		try {
			adjustParameters();
			computeImage();
			return getResultSequence();
		} finally {
			closeLociImporter();
		}
	}

	private void checkParameters() throws LargeSequenceImporterException {
		checkFile();
	}

	/**
	 * @throws LargeSequenceImporterException
	 *           If the file specified by {@link #getFilePath()} does not exist.
	 */
	private void checkFile() throws LargeSequenceImporterException {
		if (!Files.exists(getFilePath())) {
			throw new LargeSequenceImporterException(String.format("The file path does not exist: %s", getFilePath()));
		}
	}

	/**
	 * @throws LargeSequenceImporterException
	 *           If the importer cannot be opened.
	 */
	private void createLociImporter() throws LargeSequenceImporterException {
		importer = new LociImporterPlugin();
		try {
			importer.open(getFilePath().toString(), LociImporterPlugin.FLAG_METADATA_ALL);
		} catch (UnsupportedFormatException | IOException e) {
			throw new LargeSequenceImporterException(String.format("Could not open the file: %s", getFilePath()));
		}
	}

	private void adjustParameters() {
		computeTileSize();
		retrieveFileMetadata();
		adjustRetrievedRectangle();
		retrieveTargetChannelSize();
		retrieveTargetDataType();
		retrieveTargetImageName();
		retrieveTargetPixelSize();
		retrieveTargetPosition();
		computeTargetRectanglePosition();
		retrieveScaleFactor();
		computeRetrievedTileGrid();
		computeRetrievedTileSize();
		computeRetrievedImageSize();
	}

	/**
	 * @throws LargeSequenceImporterException
	 *           If the tile size cannot be read.
	 */
	private void computeTileSize() throws LargeSequenceImporterException {
		try {
			targetTileSize = new Dimension();
			targetTileSize.width = importer.getTileWidth(0);
			targetTileSize.height = importer.getTileHeight(0);
		} catch (UnsupportedFormatException | IOException e) {
			throw new LargeSequenceImporterException("Could not specify the tile size", e);
		}
		
		if (targetTileSize.width <= 0)
			targetTileSize.width = 256;
		if (targetTileSize.height <= 0)
			targetTileSize.height = 256;
		
		if (targetTileSize.width > 2048 || targetTileSize.height > 2048) {
			targetTileSize.width = 256;
			targetTileSize.height = 256;
		}
	}

	private void retrieveFileMetadata() throws LargeSequenceImporterException {
		try {
			fileMetadata = importer.getOMEXMLMetaData();
		} catch (UnsupportedFormatException | IOException e) {
			throw new LargeSequenceImporterException("Cannot retrieve metadata", e);
		}
	}

	private void adjustRetrievedRectangle() {
		int imageWidth = fileMetadata.getPixelsSizeX(0).getValue();
		int imageHeight = fileMetadata.getPixelsSizeY(0).getValue();
		targetImageSize = new Dimension(imageWidth, imageHeight);
		if (getTargetRectangle() == null || getTargetRectangle().isEmpty()) {
			setTargetPixelRectangle(new Rectangle(targetImageSize));
		} else {
			setTargetPixelRectangle(getTargetRectangle().createIntersection(new Rectangle(targetImageSize)));
		}
	}

	private void retrieveTargetChannelSize() {
		channelSize = fileMetadata.getPixelsSizeC(0).getValue();
	}

	private void retrieveTargetDataType() {
		dataType = DataType.getDataTypeFromPixelType(fileMetadata.getPixelsType(0));
	}

	private void retrieveTargetImageName() {
		targetImageName = MetaDataUtil.getName(fileMetadata, 0);
	}

	private void retrieveTargetPixelSize() {
		targetPixelSize = new Dimension2D.Double(OMEUtil.getValue(fileMetadata.getPixelsPhysicalSizeX(0), 1),
				OMEUtil.getValue(fileMetadata.getPixelsPhysicalSizeY(0), 1));
	}

	private void retrieveTargetPosition() {
		if (fileMetadata.getPlaneCount(0) > 0) {
			targetPosition = new Point2D.Double(OMEUtil.getValue(fileMetadata.getPlanePositionX(0, 0), 0),
					OMEUtil.getValue(fileMetadata.getPlanePositionY(0, 0), 0));
		} else {
			targetPosition = new Point2D.Double();
		}
	}

	private void computeTargetRectanglePosition() {
		targetRectanglePosition = new Point2D.Double(
				targetPosition.getX() + targetPixelRectangle.getX() * targetPixelSize.getWidth(),
				targetPosition.getY() + targetPixelRectangle.getY() * targetPixelSize.getHeight());
	}

	private void retrieveScaleFactor() {
		scaleFactor = 1d;
		scaleFactor /= Math.pow(2, targetResolution);
	}

	private void computeRetrievedTileGrid() {
		int xStart = (int) (getTargetRectangle().getMinX());
		int xEnd = (int) Math.ceil(getTargetRectangle().getMaxX()) - 1;
		int xStartTile = xStart / targetTileSize.width;
		int xEndTile = (xEnd + targetTileSize.width - 1) / targetTileSize.width;

		int yStart = (int) (getTargetRectangle().getMinY());
		int yEnd = (int) Math.ceil(getTargetRectangle().getMaxY()) - 1;
		int yStartTile = yStart / targetTileSize.height;
		int yEndTile = (yEnd + targetTileSize.height - 1) / targetTileSize.height;

		tileGridRectangle = new Rectangle(xStartTile, yStartTile, xEndTile - xStartTile, yEndTile - yStartTile);
	}

	private void computeRetrievedTileSize() {
		int resultTileWidth = (int) Math.ceil(targetTileSize.width * scaleFactor);
		int resultTileHeight = (int) Math.ceil(targetTileSize.height * scaleFactor);
		resultTileSize = new Dimension(resultTileWidth, resultTileHeight);
	}

	private void computeRetrievedImageSize() {
		int resultWidth = (int) (getTargetRectangle().getWidth() * scaleFactor);
		int resultHeight = (int) (getTargetRectangle().getHeight() * scaleFactor);
		resultImageSize = new Dimension(resultWidth, resultHeight);
	}

	private void computeImage() throws InterruptedException, LargeSequenceImporterException {
		createResultImage();
		resultImage.setAutoUpdateChannelBounds(true);
		resultImage.beginUpdate();
		startSubImporters();
		startThreadPool();
		CompletionService<Void> completionService = new ExecutorCompletionService<>(threadPool);
		try {
			for (int y = 0; y < tileGridRectangle.height; y++) {
				for (int x = 0; x < tileGridRectangle.width; x++) {
					completionService.submit(getTileImportationCallable(x, y));
				}
			}

			threadPool.shutdown();

			int tileNumber = 0;
			for (int y = 0; y < tileGridRectangle.height; y++) {
				for (int x = 0; x < tileGridRectangle.width; x++) {
					completionService.take().get();
					notifyProgress(tileNumber++);
				}
			}
			resultImage.endUpdate();
			resultSequence = new Sequence(resultImage);
			resultSequence.setName(targetImageName);
			resultSequence.setPositionX(targetRectanglePosition.getX());
			resultSequence.setPositionY(targetRectanglePosition.getY());
			resultSequence.setPixelSizeX(targetPixelSize.getWidth() / scaleFactor);
			resultSequence.setPixelSizeY(targetPixelSize.getHeight() / scaleFactor);
			addROIsToResultSequence();
		} catch (ExecutionException e) {
			throw new LargeSequenceImporterException("Exception while importing image: " + e);
		} finally {
			try {
				releaseThreadPool();
			} finally {
				closeSubImporters();
			}
		}

	}

	private void createResultImage() {
		resultImage = new IcyBufferedImage(resultImageSize.width, resultImageSize.height, channelSize, dataType);
	}

	private void startSubImporters() throws LargeSequenceImporterException {
		if (subImporters == null) {
			numImporters = Runtime.getRuntime().availableProcessors() * 2;
			subImporterArray = new LociImporterPlugin[numImporters];
			subImporters = new ArrayBlockingQueue<>(numImporters);

			for (int i = 0; i < numImporters; i++) {
				try {
					LociImporterPlugin subImporter = new LociImporterPlugin();
					subImporter.open(filePath.toString(), LociImporterPlugin.FLAG_METADATA_ALL);
					subImporterArray[i] = subImporter;
					subImporters.add(subImporter);
				} catch (IOException | UnsupportedFormatException e) {
					closeSubImporters();
					throw new LargeSequenceImporterException("Could not create subimporters", e);
				}
			}
		}
	}

	private void startThreadPool() {
		int threadNumber = Runtime.getRuntime().availableProcessors() * 2;
		threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadNumber);
		threadPool.prestartAllCoreThreads();
	}

	private Callable<Void> getTileImportationCallable(int x, int y) {
		return () -> {
			Thread.yield();
			Rectangle currentTileRectangle = getTileRectangle(x, y);
			Dimension currentResultTileSize = new Dimension(resultTileSize);

			if (currentTileRectangle.width < targetTileSize.width)
				currentResultTileSize.width = (int) Math.ceil(currentTileRectangle.width * scaleFactor);
			if (currentTileRectangle.height < targetTileSize.height)
				currentResultTileSize.height = (int) Math.ceil(currentTileRectangle.height * scaleFactor);

			// System.out.println(currentTileRectangle);
			LociImporterPlugin subImporter = getSubImporter();
			IcyBufferedImage tileImage;
			try {
				tileImage = subImporter.getImage(0, 0, currentTileRectangle, 0, 0);
			} finally {
				releaseSubImporter(subImporter);
			}
			Thread.yield();
			IcyBufferedImage scaledImage = IcyBufferedImageUtil.scale(tileImage, currentResultTileSize.width,
					currentResultTileSize.height);
			Point tilePosition = getTilePositionInResultImage(x, y);
			resultImage.copyData(scaledImage, null, tilePosition);
			resultImage.dataChanged();
			return null;
		};
	}

	private Rectangle getTileRectangle(int x, int y) {
		Rectangle tileRectangle = new Rectangle((tileGridRectangle.x + x) * targetTileSize.width,
				(tileGridRectangle.y + y) * targetTileSize.height, targetTileSize.width, targetTileSize.height);

		if (targetImageSize.getWidth() < tileRectangle.getMaxX())
			tileRectangle.width -= (int) Math.ceil(tileRectangle.getMaxX() - targetImageSize.getWidth());
		if (targetImageSize.getHeight() < tileRectangle.getMaxY())
			tileRectangle.height -= (int) Math.ceil(tileRectangle.getMaxY() - targetImageSize.getHeight());

		return tileRectangle;
	}

	private LociImporterPlugin getSubImporter() throws InterruptedException {
		return subImporters.take();
	}

	private void releaseSubImporter(LociImporterPlugin subImporter) throws InterruptedException {
		subImporters.put(subImporter);
	}

	private Point getTilePositionInResultImage(int x, int y) {
		Point tilePositionInTargetImage = getTilePositionInTargetImage(x, y);
		int tileX = (int) ((tilePositionInTargetImage.x - targetPixelRectangle.getX()) * scaleFactor);
		int tileY = (int) ((tilePositionInTargetImage.y - targetPixelRectangle.getY()) * scaleFactor);
		return new Point(tileX, tileY);
	}

	private Point getTilePositionInTargetImage(int x, int y) {
		Point tileGridPosition = getTileGridPosition(x, y);
		return new Point(tileGridPosition.x * targetTileSize.width, tileGridPosition.y * targetTileSize.height);
	}

	private Point getTileGridPosition(int x, int y) {
		return new Point(tileGridRectangle.x + x, tileGridRectangle.y + y);
	}

	private void closeSubImporters() throws LargeSequenceImporterException {
		if (subImporterArray != null) {
			try {
				for (int i = 0; i < numImporters; i++) {
					if (subImporterArray[i] != null) {
						try {
							subImporterArray[i].close();
							subImporterArray[i] = null;
						} catch (Exception e) {
							throw new LargeSequenceImporterException("Could not close all subimporters.", e);
						}
					}
				}
			} finally {
				subImporterArray = null;
				numImporters = 0;
			}
		}
	}

	private void notifyProgress(int tileNumber) {
		int totalTileNumber = tileGridRectangle.width * tileGridRectangle.height;
		progressListeners.forEach(l -> l.notifyProgress(tileNumber / (double) totalTileNumber,
				String.format("Loading tiles (%d/%d)", tileNumber, totalTileNumber), null));
	}

	private void addROIsToResultSequence() {
		String fileName = FileUtil.getFileName(getFilePath().toString(), false);
		Path xmlFile = getFilePath().resolveSibling(fileName + ".xml");
		if (Files.exists(xmlFile)) {
			Document xml = XMLUtil.loadDocument(xmlFile.toFile());
			Element rootElement = XMLUtil.getRootElement(xml);
			Element roisElement = XMLUtil.getElement(rootElement, "rois");
			List<? extends ROI> rois = ROI.loadROIsFromXML(roisElement);

			Sequence placeholderSequence = new Sequence();
			placeholderSequence.setPixelSizeX(targetPixelSize.getWidth());
			placeholderSequence.setPixelSizeY(targetPixelSize.getHeight());
			placeholderSequence.setPositionX(targetPosition.getX());
			placeholderSequence.setPositionY(targetPosition.getY());

			rois.stream().filter(roi -> roi instanceof ROI2DShape).map(roi -> (ROI2DShape) roi)
					.filter(shapeRoi -> shapeRoi.intersects(targetPixelRectangle))
					.map(shapeRoi -> ROIUtil.adjustToSequence(shapeRoi, placeholderSequence, getResultSequence()))
					.forEach(scaledRoi -> getResultSequence().addROI(scaledRoi));
		}
	}

	private void releaseThreadPool() throws InterruptedException {
		threadPool.shutdownNow();
		if (!threadPool.awaitTermination(1, TimeUnit.SECONDS)) {
			throw new LargeSequenceImporterException("Importer threads did not finish");
		}
	}

	/**
	 * @throws LargeSequenceImporterException
	 *           If the importer cannot be closed.
	 */
	private void closeLociImporter() throws LargeSequenceImporterException {
		try {
			importer.close();
		} catch (IOException e) {
			throw new LargeSequenceImporterException(String.format("Could not close Loci importer for %s", getFilePath()), e);
		}
	}

	private Sequence getResultSequence() {
		return resultSequence;
	}
}
