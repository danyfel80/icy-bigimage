package algorithms.danyfel80.io.image.big;

import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import algorithms.danyfel80.io.image.TileProvider;
import icy.common.listener.DetailedProgressListener;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.sequence.MetaDataUtil;
import icy.type.DataType;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.ome.OMEXMLMetadata;
import loci.formats.out.OMETiffWriter;
import loci.formats.tiff.IFD;
import loci.formats.tiff.TiffCompression;
import ome.xml.meta.IMetadata;
import ome.xml.model.primitives.PositiveInteger;

public class BigImageExporter implements AutoCloseable {
	public static int tileWidth = 256;
	public static int tileHeight = 256;

	public static OMEXMLMetadata createMetadata(int width, int height, int channels, DataType dataType)
			throws DependencyException, ServiceException {
		OMEXMLMetadata metadata = (OMEXMLMetadata) MetaDataUtil.generateMetaData(width, height, channels, 1, 1, dataType,
				getSeparateChannelFlag(channels, dataType));
		if (metadata.getChannelCount(0) == 1
				&& metadata.getChannelSamplesPerPixel(0, 0).getValue() == 1)
			metadata.setChannelSamplesPerPixel(new PositiveInteger(1), 0, 0);
		return metadata;
	}

	public static boolean getSeparateChannelFlag(int channels, DataType dataType) {
		// Only if we have more than 1 channel
		if (channels > 1) {
			// Only channel amount is different than three and data type size is more
			// than 1 byte
			return (channels != 3) || (dataType.getSize() > 1);
		}
		// Otherwise use fused channels
		return false;
	}

	private File file;
	private DetailedProgressListener progressListener;
	private IMetadata metadata;
	private OMETiffWriter writer;

	public BigImageExporter(File file, IMetadata metadata) throws FormatException, IOException {
		this.file = file;
		this.metadata = metadata;
		this.writer = initializeWriter(metadata);
	}

	public void setProgressListener(DetailedProgressListener listener) {
		this.progressListener = listener;
	}

	private void notifyProgress(double progress, String message, Object data) {
		if (progressListener != null) {
			progressListener.notifyProgress(progress, "Exporting: " + message, null);
		}
	}

	private OMETiffWriter initializeWriter(IMetadata metadata) throws FormatException, IOException {
		OMETiffWriter writer = new OMETiffWriter();
		writer.setCompression(TiffCompression.LZW.getCodecName());
		int chs = metadata.getChannelCount(0);
		for (int ch = 0; ch < chs; ch++) {
			metadata.setPixelsInterleaved(false, 0);
		}
		writer.setMetadataRetrieve((MetadataRetrieve) metadata);
		writer.setWriteSequentially(true);
		writer.setInterleaved(false);
		writer.setBigTiff(true);

		Files.deleteIfExists(file.toPath());
		writer.setId(file.getAbsolutePath());
		writer.setSeries(0);
		return writer;
	}

	public void write(TileProvider tileProvider) throws InterruptedException, IOException, FormatException {
		notifyProgress(Double.NaN, "Starting to write...", null);
		int x, y;
		byte[] tile = null;

		long[] rowsPerStrip;
		int w, h;
		IFD ifd;
		int count = MetaDataUtil.getNumChannel((ome.xml.meta.OMEXMLMetadata) metadata, 0);
		int sizeX, sizeY;
		boolean separateChannels = MetaDataUtil.getNumChannel((ome.xml.meta.OMEXMLMetadata) metadata, 0) != 1;

		int tilesX, tilesY;
		int diffWidth, diffHeight;

		rowsPerStrip = new long[1];
		rowsPerStrip[0] = tileHeight;

		int series = 1;
		for (int s = 0; s < series; s++) {
			sizeX = metadata.getPixelsSizeX(0).getValue();
			sizeY = metadata.getPixelsSizeY(0).getValue();
			if (tileWidth <= 0)
				tileWidth = sizeX;
			if (tileHeight <= 0)
				tileHeight = sizeY;
			tilesX = sizeX / tileWidth;
			tilesY = sizeY / tileHeight;
			if (tilesX == 0) {
				tileWidth = sizeX;
				tilesX = 1;
			}
			if (tilesY == 0) {
				tileHeight = sizeY;
				tilesY = 1;
			}
			diffWidth = sizeX - tilesX * tileWidth;
			diffHeight = sizeY - tilesY * tileHeight;
			if (diffWidth > 0)
				tilesX++;
			if (diffHeight > 0)
				tilesY++;

			int totalTiles = tilesY * tilesX * count;
			int tilesProcessed = 0;
			
			for (int k = 0; k < count; k++) {
				x = 0;
				y = 0;
				ifd = new IFD();
				ifd.put(IFD.TILE_WIDTH, tileWidth);
				ifd.put(IFD.TILE_LENGTH, tileHeight);
				ifd.put(IFD.ROWS_PER_STRIP, rowsPerStrip);
				for (int i = 0; i < tilesY; i++) {
					y = tileHeight * i;
					if (diffHeight > 0 && i == (tilesY - 1)) {
						h = diffHeight;
					} else {
						h = tileHeight;
					}
					for (int j = 0; j < tilesX; j++) {
						if (Thread.currentThread().isInterrupted())
							throw new InterruptedException();
						++tilesProcessed;
						x = tileWidth * j;
						if (diffWidth > 0 && j == (tilesX - 1)) {
							w = diffWidth;
						} else {
							w = tileWidth;
						}

						IcyBufferedImage tileImage = tileProvider.getTile(new Point(j, i));
						notifyProgress(tilesProcessed / (double) totalTiles,
								String.format("Tile (%d/%d)", tilesProcessed, totalTiles), tileImage);

						if (separateChannels) {
							tile = tileImage.getRawData(k, !metadata.getPixelsBinDataBigEndian(0, 0));
						} else {
							tile = tileImage.getRawData(!metadata.getPixelsBinDataBigEndian(0, 0));
						}
						if (tileImage.getWidth() < w || tileImage.getHeight() < h)
							throw new IOException(String.format("Tile size not coherent: Tile (%d, %d), expected (%d, %d)",
									tileImage.getWidth(), tileImage.getHeight(), w, h));
						if (tileImage.getWidth() != w || tileImage.getHeight() != h)
							tileImage = IcyBufferedImageUtil.getSubImage(tileImage, 0, 0, w, h);
						writer.saveBytes(k, tile, ifd, x, y, w, h);
					}
				}
			}
		}
	}

	@Override
	public void close() throws Exception {
		this.writer.close();
	}
}
