package algorithms.danyfel80.bigimage.io;

import java.awt.Dimension;
import java.awt.Point;
import java.io.File;
import java.io.IOException;

import icy.file.FileUtil;
import icy.image.IcyBufferedImage;
import icy.sequence.MetaDataUtil;
import icy.type.DataType;
import icy.util.OMEUtil;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import loci.formats.IFormatWriter;
import loci.formats.ome.OMEXMLMetadataImpl;
import loci.formats.out.OMETiffWriter;
import loci.formats.out.TiffWriter;
import loci.formats.tiff.IFD;
import loci.formats.tiff.TiffCompression;

/**
 * This class is in charge of opening the stream, write data and closing the
 * stream for a big image file.
 * 
 * @author Daniel Felipe Gonzalez Obando
 */
public class BigImageWriter {

	private final File path;
	private final Dimension imgSize;
	private final int imgChannelSize;
	private final DataType imgDataType;
	private final Dimension tileSize;

	private IFD ifd;
	private boolean isSeparateChannels;
	private boolean isLittleEndian;

	private TiffWriter writer;
	
	private boolean firstWrite;

	public BigImageWriter(File path, Dimension imgSize, int imgChannelSize, DataType imgDataType, Dimension tileSize)
	    throws ServiceException, FormatException, IOException {
		// Set parameters
		if (path == null) {
			throw new IllegalArgumentException("Invalid file path: " + path);
		}
		this.path = path;

		if (imgSize == null || imgSize.width * imgSize.height == 0) {
			throw new IllegalArgumentException("Invalid image size: " + imgSize);
		}
		this.imgSize = imgSize;

		if (imgChannelSize < 1) {
			throw new IllegalArgumentException("Invalid channel size: " + imgChannelSize);
		}
		this.imgChannelSize = imgChannelSize;

		if (imgDataType == null) {
			throw new IllegalArgumentException("Invalid data type: " + imgDataType);
		}
		this.imgDataType = imgDataType;

		if (tileSize == null || tileSize.width * tileSize.height == 0) {
			throw new IllegalArgumentException("Invalid tile size: " + tileSize);
		}
		this.tileSize = tileSize;

		// Init writer
		initializeWriter();
		writer.setSeries(0);
	}

	/**
	 * Creates the file to write the image to. If the file already exist it will be overwritten.
	 * @throws ServiceException If metadata is not correctly created.
	 * @throws FormatException If compression method is not valid.
	 * @throws IOException If file is not correctly created.
	 */
	private void initializeWriter() throws ServiceException, FormatException, IOException {
		writer = new TiffWriter();

		OMEXMLMetadataImpl mdi = OMEUtil.createOMEMetadata();
		this.isSeparateChannels = getSeparateChannelFlag(writer, imgChannelSize, imgDataType);
		MetaDataUtil.setMetaData(mdi, imgSize.width, imgSize.height, imgChannelSize, 1, 1, -1, -1, imgDataType,
		    isSeparateChannels);
		this.isLittleEndian = !mdi.getPixelsBinDataBigEndian(0, 0);
		writer.setMetadataRetrieve(mdi);
		writer.setCompression(TiffCompression.LZW.getCodecName());
		writer.setWriteSequentially(true);
		writer.setInterleaved(false);
		writer.setBigTiff(true);
		if (FileUtil.exists(path.getAbsolutePath())) {
			FileUtil.delete(path, true);
		}
		writer.setId(path.getAbsolutePath());

		ifd = new IFD();
		long[] rowPerStrip = new long[1];
		rowPerStrip[0] = tileSize.height;
		ifd.put(IFD.TILE_WIDTH, tileSize.width);
		ifd.put(IFD.TILE_LENGTH, tileSize.height);
		ifd.put(IFD.ROWS_PER_STRIP, rowPerStrip);
		firstWrite = true;
	}
	
	/**
	 * Return the separate channel flag from specified writer and color space
	 */
	private static boolean getSeparateChannelFlag(IFormatWriter writer, int numChannel, DataType dataType) {
		if (writer instanceof OMETiffWriter)
			return (numChannel == 2) || (numChannel > 4) || (dataType.getSize() > 1);
		return false;
	}
	
	/**
	 * Closes the writing stream of the file.
	 * @throws IOException If the file cannot properly be closed.
	 */
	public void closeWriter() throws IOException {
		this.writer.close();
	}
	
	/**
	 * Writes a tile to the image file
	 * @param imageTile Image tile to write
	 * @param tgtPoint Position of the tile to write
	 * @throws IOException if the tile cannot be writen to file.
	 * @throws FormatException
	 */
	public void saveTile(IcyBufferedImage imageTile, Point tgtPoint)
	    throws IOException, FormatException {

		// To make sure IDF is created only once
		synchronized (this) {
			if (firstWrite) {
				writeData(imageTile, tgtPoint);
				firstWrite = false;
				return;
			}
		}
		
		if (!firstWrite) {
			writeData(imageTile, tgtPoint);
		}
	}
	
	private void writeData(IcyBufferedImage imageTile, Point tgtPoint)
	    throws IOException, FormatException {
		byte[] data = null;

		// separated channel data
		if (isSeparateChannels) {
			for (int c = 0; c < imgChannelSize; c++) {
				
				if (imageTile != null) {
					data = imageTile.getRawData(c, isLittleEndian);
					writer.saveBytes(c, data, ifd, tgtPoint.x, tgtPoint.y, imageTile.getSizeX(), imageTile.getSizeY());
				}
			}
		} else {
			if (imageTile != null) {
				data = imageTile.getRawData(isLittleEndian);
				writer.saveBytes(0, data, ifd, tgtPoint.x, tgtPoint.y, imageTile.getSizeX(), imageTile.getSizeY());
			}
		}
	}
}
