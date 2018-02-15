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
package algorithms.danyfel80.io.image.big;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.IntStream;

import icy.common.listener.DetailedProgressListener;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.sequence.Sequence;
import icy.type.DataType;
import plugins.kernel.importer.LociImporterPlugin;

/**
 * @author Daniel Felipe Gonzalez Obando
 *
 */
public class BigImageImporter implements Callable<Sequence> {

	private int tileSizeX;
	private int tileSizeY;

	private File file;
	private int resolution;
	private Rectangle regionToImport;
	private DetailedProgressListener progressListener;

	public BigImageImporter(File filePath, int resolution, Rectangle region) {
		if (!filePath.isFile())
			throw new IllegalArgumentException("The given path is not a file. (" + filePath + ")");
		this.file = filePath;

		if (resolution < 0)
			throw new IllegalArgumentException("Negative resolution " + resolution);
		this.resolution = resolution;

		this.regionToImport = region;
	}

	public void setRegionToImport(Rectangle region) {
		this.regionToImport = region;
	}

	public void setProgressListener(DetailedProgressListener listener) {
		this.progressListener = listener;
	}

	private void notifyProgress(double progress, String message) {
		if (progressListener != null) {
			progressListener.notifyProgress(progress, message, null);
		}
	}

	@Override
	public synchronized Sequence call() throws Exception {
		notifyProgress(Double.NaN, "Loading image...");
		// Get image data
		final Rectangle imageRegion = new Rectangle(BigImageUtil.getImageDimension(file));
		if (regionToImport == null || regionToImport.isEmpty()) {
			regionToImport = new Rectangle(imageRegion);
		}
		checkRegion(imageRegion);
		final int channels = BigImageUtil.getImageChannelCount(file);
		final DataType type = BigImageUtil.getImageDataType(file);
		final double scalingFactor = 1d / getScalingFactor(resolution);
		final double[] pixelSize = Arrays.stream(BigImageUtil.getImagePixelSize(file))
				.mapToDouble(i -> i.value().doubleValue()).toArray();
		computeTileSize(regionToImport, channels, type);

		// Create blank image
		final double[] outputPixelSize = Arrays.stream(BigImageUtil.getImagePixelSize(file))
				.mapToDouble(i -> i.value().doubleValue()).toArray();
		final Rectangle scaledRegionToImport = new Rectangle(regionToImport);
		IntStream.range(0, resolution).forEach(i -> {
			scaledRegionToImport.x = Math.floorDiv(scaledRegionToImport.x, 2);
			scaledRegionToImport.y = Math.floorDiv(scaledRegionToImport.y, 2);
			scaledRegionToImport.width = Math.floorDiv(scaledRegionToImport.width, 2);
			scaledRegionToImport.height = Math.floorDiv(scaledRegionToImport.height, 2);
			outputPixelSize[0] *= 2d;
			outputPixelSize[1] *= 2d;
		});
		final IcyBufferedImage image = new IcyBufferedImage(scaledRegionToImport.width, scaledRegionToImport.height,
				channels, type);

		final Rectangle imageTileRectangle = new Rectangle(new Dimension((imageRegion.width + tileSizeX - 1) / tileSizeX,
				(imageRegion.height + tileSizeY - 1) / tileSizeY));

		// Find tiles to load from file
		final Point regionToImportTileStartPoint = new Point(Math.floorDiv(regionToImport.x, tileSizeX),
				Math.floorDiv(regionToImport.y, tileSizeY));
		final Point regionToImportTileEndPoint = new Point(
				Math.floorDiv(regionToImport.x + regionToImport.width + tileSizeX - 1, tileSizeX),
				Math.floorDiv(regionToImport.y + regionToImport.height + tileSizeY - 1, tileSizeY));
		final Dimension regionToImportTileDimension = new Dimension(
				regionToImportTileEndPoint.x - regionToImportTileStartPoint.x,
				regionToImportTileEndPoint.y - regionToImportTileStartPoint.y);
		final Rectangle regionToImportTileRectangle = new Rectangle(regionToImportTileStartPoint,
				regionToImportTileDimension);
		final Rectangle loadedTileRectangle = imageTileRectangle.intersection(regionToImportTileRectangle);

		// Fill image with loaded tiles
		int numProcessors = Runtime.getRuntime().availableProcessors();
		if (regionToImport.width < 1000 && regionToImport.height < 1000) {
			numProcessors = 1;
		}
		LociImporterPlugin importer = new LociImporterPlugin();
		importer.open(file.getAbsolutePath(), 0);
		try {
			ThreadPoolExecutor tp = (ThreadPoolExecutor) Executors.newFixedThreadPool(numProcessors);
			ExecutorCompletionService<Void> cs = new ExecutorCompletionService<>(tp);
			for (int tx = loadedTileRectangle.x; tx < loadedTileRectangle.x + loadedTileRectangle.width; tx++) {
				final int txf = tx;
				for (int ty = loadedTileRectangle.y; ty < loadedTileRectangle.y + loadedTileRectangle.height; ty++) {
					final int tyf = ty;
					final Rectangle tileRegionToLoad = new Rectangle(txf * tileSizeX, tyf * tileSizeY, tileSizeX, tileSizeY)
							.intersection(imageRegion);
					cs.submit(() -> {
						IcyBufferedImage tileImage = LociImporterPlugin.getImage(importer.getReader(), tileRegionToLoad, 0, 0);

						if (Thread.currentThread().isInterrupted())
							throw new InterruptedException();

						tileImage = IcyBufferedImageUtil.scale(tileImage, (int) (tileImage.getWidth() * scalingFactor),
								(int) (tileImage.getHeight() * scalingFactor));
						Point scaledPosition = new Point((int) ((txf * tileSizeX - regionToImport.x) * scalingFactor),
								(int) ((tyf * tileSizeY - regionToImport.y) * scalingFactor));

						if (Thread.currentThread().isInterrupted())
							throw new InterruptedException();

						image.copyData(tileImage, null, scaledPosition);
						return null;
					});
				}
			}
			tp.shutdown();

			int totalTiles = loadedTileRectangle.width * loadedTileRectangle.height;

			try {
				for (int progress = 0; progress < totalTiles; progress++) {
					cs.take().get();
					notifyProgress(progress / (double) totalTiles,
							String.format("Loading image... (%d tiles loaded of %d)", progress, totalTiles));
				}
				notifyProgress(1d, "Done loading image");
			} finally {
				// for (Graphics2D g1 : gs) {
				// g1.dispose();
				// }
				// g.dispose();
			}
		} finally {
			importer.close();
		}
		// Create sequence with resulting image
		Sequence result = new Sequence(image);
		result.setName(BigImageUtil.getImageName(file));
		result.setPixelSizeX(outputPixelSize[0]);
		result.setPixelSizeY(outputPixelSize[1]);
		result.setPositionX(regionToImport.getX() * pixelSize[0]);
		result.setPositionY(regionToImport.getY() * pixelSize[1]);
		return result;
	}

	private int getScalingFactor(int resolution) {
		int scale = 1;
		for (int i = 0; i < resolution; i++) {
			scale *= 2;
		}
		return scale;
	}

	private void checkRegion(Rectangle imageRegion) throws IOException {
		boolean intersects = imageRegion.intersects(regionToImport);
		if (!intersects)
			throw new IOException("Region to load does not intersect the image (" + regionToImport + ")");

	}

	private void computeTileSize(Rectangle regionToImport, int channels, DataType type) {
		long usedMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
		long ramAvailable = Runtime.getRuntime().maxMemory() - usedMem;
		ramAvailable = (long) (ramAvailable * 0.75);
		long processors = Runtime.getRuntime().availableProcessors();

		tileSizeX = 256;
		tileSizeY = 256;

		if (regionToImport.width < 1000 && regionToImport.height < 1000) {
			return;
		}

		long totalMemoryUsed = (tileSizeX * tileSizeY * channels * type.getSize() * processors)
				+ (regionToImport.width * regionToImport.height * channels * type.getSize());
		long totalTiles = regionToImport.width / tileSizeX + regionToImport.height / tileSizeY;
		while (totalMemoryUsed < ramAvailable && totalTiles > processors) {
			tileSizeX *= 2;
			tileSizeY *= 2;
			tileSizeX = Math.min(tileSizeX, regionToImport.width);
			tileSizeY = Math.min(tileSizeY, regionToImport.height);
			totalTiles = regionToImport.width / tileSizeX + regionToImport.height / tileSizeY;
			totalMemoryUsed = tileSizeX * tileSizeY * channels * type.getSize() * processors;
		}
		tileSizeX /= 2;
		tileSizeY /= 2;
	}

}
