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
import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import algorithms.danyfel80.io.sequence.tileprovider.ITileProvider;
import icy.common.listener.DetailedProgressListener;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.sequence.MetaDataUtil;
import icy.type.DataType;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import loci.formats.ome.OMEXMLMetadata;
import loci.formats.out.OMETiffWriter;
import loci.formats.tiff.IFD;
import loci.formats.tiff.TiffCompression;
import ome.xml.model.primitives.PositiveInteger;

/**
 * @author Daniel Felipe Gonzalez Obando
 *
 */
public class LargeSequenceExporter implements AutoCloseable {

	public static OMEXMLMetadata createMetadata(int width, int height, int channelSize, DataType dataType)
			throws LargeSequenceExporterException {
		OMEXMLMetadata metadata;
		try {
			metadata = (OMEXMLMetadata) MetaDataUtil.generateMetaData(width, height, channelSize, 1, 1, dataType,
					getSeparateChannelFlag(channelSize, dataType));
		} catch (ServiceException e) {
			throw new LargeSequenceExporterException(String.format(
					"Could not create metadata for image (w=%s,h=%s,ch=%s,type=%s)", width, height, channelSize, dataType), e);
		}

		adjustSamplesPerPixel(metadata);
		return metadata;
	}

	private static boolean getSeparateChannelFlag(int channels, DataType dataType) {
		// Check for separate channels
		if (channels > 1) {
			// Channel count must be different than 3 to be separated
			// Data type size must be more than just 1 byte to be separated
			return (channels != 3) || (dataType.getSize() > 1);
		}
		// Else use fused channels
		return false;
	}

	private static void adjustSamplesPerPixel(OMEXMLMetadata metadata) {
		if (metadata.getChannelCount(0) == 1 && metadata.getChannelSamplesPerPixel(0, 0).getValue() == 1)
			metadata.setChannelSamplesPerPixel(new PositiveInteger(1), 0, 0);
	}

	public Dimension TILE_SIZE = new Dimension(256, 256);

	private Path outputFilePath;
	private OMEXMLMetadata outputImageMetadata;
	private ITileProvider tileProvider;
	private Set<DetailedProgressListener> progressListeners;

	private OMETiffWriter imageWriter;

	// Writing params
	private long[] rowsPerStrip;
	private Dimension imageSize;
	private Dimension tileGridSize;
	private int seriesSize;

	private Dimension imageSizeDifference;

	private int totalTiles;
	private int tilesProcessed;

	private int channelSize;
	private boolean usingSeparateChannels;
	private IFD ifd;

	private int currentSeries;
	private int currentChannel;

	private int currentTileRow;
	private int currentTileY;
	private int currentTileHeight;

	private int currentTileColumn;
	private int currentTileX;
	private int currentTileWidth;

	private byte[] currentTileData;

	private IcyBufferedImage currentTileImage;

	public LargeSequenceExporter() {
		progressListeners = new HashSet<>();
	}

	public Path getOutputFilePath() {
		return outputFilePath;
	}

	public void setOutputFilePath(Path outputFilePath) {
		this.outputFilePath = outputFilePath;
	}

	public OMEXMLMetadata getOutputImageMetadata() {
		return outputImageMetadata;
	}

	public void setOutputImageMetadata(OMEXMLMetadata outputImageMetadata) {
		this.outputImageMetadata = outputImageMetadata;
	}

	public ITileProvider getTileProvider() {
		return tileProvider;
	}

	public void setTileProvider(ITileProvider tileProvider) {
		this.tileProvider = tileProvider;
	}

	public void addProgressListener(DetailedProgressListener listener) {
		this.progressListeners.add(listener);
	}

	public void removeProgressListener(DetailedProgressListener listener) {
		this.progressListeners.remove(listener);
	}

	public void write() throws InterruptedException, IOException, FormatException {
		initializeWriter();

		notifyCurrentProgress();
		rowsPerStrip = new long[1];
		rowsPerStrip[0] = TILE_SIZE.height;

		seriesSize = 1;
		retrieveImageSize();
		checkTileSize();
		setTileGridSize();
		checkComplementaryTiles();
		channelSize = MetaDataUtil.getNumChannel(outputImageMetadata, 0);

		totalTiles = tileGridSize.width * tileGridSize.height * channelSize * seriesSize;
		tilesProcessed = 0;

		for (currentSeries = 0; currentSeries < seriesSize; currentSeries++) {
			writeSeries();
		}
	}

	private void initializeWriter() throws LargeSequenceExporterException {
		checkParameters();
		createOuputFile();
	}

	private void checkParameters() throws LargeSequenceExporterException {
		checkOutputFilePath();
		checkMetadata();
		checkTileProvider();
	}

	private void checkOutputFilePath() throws LargeSequenceExporterException {
		if (outputFilePath == null)
			throw new LargeSequenceExporterException("No output file specified");
	}

	private void checkMetadata() throws LargeSequenceExporterException {
		if (outputImageMetadata == null)
			throw new LargeSequenceExporterException("No output file medatada specified");
	}

	private void checkTileProvider() throws LargeSequenceExporterException {
		if (tileProvider == null)
			throw new LargeSequenceExporterException("No tile provider specified");
	}

	private void createOuputFile() throws LargeSequenceExporterException {
		imageWriter = new OMETiffWriter();
		setWriterCompression();
		setPixelsNotInterleaved();
		imageWriter.setMetadataRetrieve(outputImageMetadata);
		imageWriter.setWriteSequentially(true);
		imageWriter.setInterleaved(false);
		imageWriter.setBigTiff(true);

		deleteExisitingFile();
		setWrittenFile();
		setWriterAtFirstSeries();
	}

	private void setWriterCompression() throws LargeSequenceExporterException {
		try {
			imageWriter.setCompression(TiffCompression.LZW.getCodecName());
		} catch (FormatException e) {
			throw new LargeSequenceExporterException(
					String.format("Format not supported: %s", TiffCompression.LZW.getCodecName()), e);
		}
	}

	private void setPixelsNotInterleaved() throws LargeSequenceExporterException {
		int chs = outputImageMetadata.getChannelCount(0);
		for (int ch = 0; ch < chs; ch++) {
			outputImageMetadata.setPixelsInterleaved(false, 0);
		}
	}

	private void deleteExisitingFile() throws LargeSequenceExporterException {
		try {
			Files.deleteIfExists(outputFilePath);
		} catch (IOException e) {
			throw new LargeSequenceExporterException(
					String.format("Could not delete existing output file: %s", outputFilePath), e);
		}
	}

	private void setWrittenFile() throws LargeSequenceExporterException {
		try {
			imageWriter.setId(outputFilePath.toAbsolutePath().toString());
		} catch (IOException e) {
			throw new LargeSequenceExporterException(String.format("Could not create output file: %s", outputFilePath), e);
		} catch (FormatException e) {
			throw new LargeSequenceExporterException(String.format("Output file format is not supported: %s", outputFilePath),
					e);
		}
	}

	private void setWriterAtFirstSeries() throws LargeSequenceExporterException {
		try {
			imageWriter.setSeries(0);
		} catch (FormatException e) {
			try {
				imageWriter.close();
			} catch (IOException e1) {
				throw new LargeSequenceExporterException("Cannot close image writer after exception: ", e1);
			}
			throw new LargeSequenceExporterException(
					String.format("Cannot set the current written series to 0", outputFilePath), e);
		}
	}

	private void notifyCurrentProgress() {
		double progress = (tilesProcessed > 0) ? (tilesProcessed / (double) totalTiles) : Double.NaN;
		String message = (tilesProcessed > 0) ? String.format("Writing tile %d of %d...", tilesProcessed, totalTiles)
				: "Initializing file writing...";
		progressListeners.forEach(l -> l.notifyProgress(progress, message, null));
	}

	private void retrieveImageSize() {
		int sizeX = outputImageMetadata.getPixelsSizeX(0).getValue();
		int sizeY = outputImageMetadata.getPixelsSizeY(0).getValue();
		imageSize = new Dimension(sizeX, sizeY);
	}

	private void checkTileSize() {
		if (TILE_SIZE.width <= 0)
			TILE_SIZE.width = imageSize.width;
		if (TILE_SIZE.height <= 0)
			TILE_SIZE.height = imageSize.height;
	}

	private void setTileGridSize() {
		int sizeX = imageSize.width / TILE_SIZE.width;
		int sizeY = imageSize.height / TILE_SIZE.height;
		if (sizeX == 0) {
			TILE_SIZE.width = imageSize.width;
			sizeX = 1;
		}
		if (sizeY == 0) {
			TILE_SIZE.height = imageSize.width;
			sizeY = 1;
		}
		tileGridSize = new Dimension(sizeX, sizeY);
	}

	private void checkComplementaryTiles() {
		int imageSizeDifferenceX = imageSize.width - tileGridSize.width * TILE_SIZE.width;
		int imageSizeDifferenceY = imageSize.height - tileGridSize.height * TILE_SIZE.height;
		imageSizeDifference = new Dimension(imageSizeDifferenceX, imageSizeDifferenceY);
		if (imageSizeDifference.width > 0)
			tileGridSize.width++;
		if (imageSizeDifference.height > 0)
			tileGridSize.height++;
	}

	private void writeSeries() throws InterruptedException, IOException, FormatException {
		usingSeparateChannels = MetaDataUtil.getNumChannel(outputImageMetadata, currentSeries) != 1;
		ifd = createIFD();
		for (currentChannel = 0; currentChannel < channelSize; currentChannel++) {
			writeChannel();
		}
	}

	private IFD createIFD() {
		IFD ifd = new IFD();
		ifd.put(IFD.TILE_WIDTH, TILE_SIZE.width);
		ifd.put(IFD.TILE_LENGTH, TILE_SIZE.height);
		ifd.put(IFD.ROWS_PER_STRIP, rowsPerStrip);
		return ifd;
	}

	private void writeChannel() throws InterruptedException, IOException, FormatException {

		for (currentTileRow = 0; currentTileRow < tileGridSize.height; currentTileRow++) {
			writeTileRow();
		}
	}

	private void writeTileRow() throws InterruptedException, IOException, FormatException {
		currentTileY = TILE_SIZE.height * currentTileRow;
		if (imageSizeDifference.height > 0 && currentTileRow == (tileGridSize.height - 1)) {
			currentTileHeight = imageSizeDifference.height;
		} else {
			currentTileHeight = TILE_SIZE.height;
		}

		for (currentTileColumn = 0; currentTileColumn < tileGridSize.width; currentTileColumn++) {
			writeTile();
		}
	}

	private void writeTile() throws InterruptedException, IOException, FormatException {
		if (Thread.currentThread().isInterrupted())
			throw new InterruptedException();

		++tilesProcessed;
		notifyCurrentProgress();

		currentTileX = TILE_SIZE.width * currentTileColumn;
		currentTileWidth = getCurrentTileWidth();

		getCurrentTileImage();
		getCurrentTileData();

		try {
			imageWriter.saveBytes(currentChannel, currentTileData, ifd, currentTileX, currentTileY, currentTileWidth,
				currentTileHeight);
		}catch (ClosedByInterruptException e) {
			throw new InterruptedException();
		}
	}

	private int getCurrentTileWidth() {
		if (imageSizeDifference.width > 0 && currentTileColumn == (tileGridSize.width - 1)) {
			return imageSizeDifference.width;
		} else {
			return TILE_SIZE.width;
		}
	}

	private void getCurrentTileImage() throws IOException {
		currentTileImage = tileProvider.getTile(new Point(currentTileColumn, currentTileRow));

		if (currentTileImage.getWidth() < currentTileWidth || currentTileImage.getHeight() < currentTileHeight)
			throw new IOException(String.format("Tile size not coherent: Tile (%d, %d), expected (%d, %d)",
					currentTileImage.getWidth(), currentTileImage.getHeight(), currentTileWidth, currentTileHeight));
		if (currentTileImage.getWidth() != currentTileWidth || currentTileImage.getHeight() != currentTileHeight)
			currentTileImage = IcyBufferedImageUtil.getSubImage(currentTileImage, 0, 0, currentTileWidth, currentTileHeight);
	}

	private void getCurrentTileData() {
		if (usingSeparateChannels) {
			currentTileData = currentTileImage.getRawData(currentChannel,
					!outputImageMetadata.getPixelsBinDataBigEndian(0, 0));
		} else {
			currentTileData = currentTileImage.getRawData(!outputImageMetadata.getPixelsBinDataBigEndian(0, 0));
		}
	}

	@Override
	public void close() throws Exception {
		progressListeners.clear();
		if (imageWriter != null) {
			imageWriter.close();
			imageWriter = null;
		}
	}
}
