/**
 * 
 */
package algorithms.danyfel80.io.sequence.large;

import java.awt.Dimension;
import java.io.File;
import java.io.IOException;

import icy.common.exception.UnsupportedFormatException;
import icy.sequence.MetaDataUtil;
import icy.type.DataType;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.xml.meta.OMEXMLMetadata;
import plugins.kernel.importer.LociImporterPlugin;

/**
 * This is an utilitary class to provide information about big image files.
 * 
 * @author Daniel Felipe Gonzalez Obando
 */
public class LargeSequenceHelper {

	/**
	 * Retrieves the 2D dimension of the image of the given file path.
	 * 
	 * @param path
	 *          Path of the file.
	 * @return The dimension of the image in the specified file.
	 * @throws UnsupportedFormatException
	 *           If the file can't be read.
	 * @throws IOException
	 *           If the file doesn't exist.
	 */
	public static Dimension getImageDimension(File path) throws IOException, UnsupportedFormatException {
		LociImporterPlugin importer = new LociImporterPlugin();
		try {
			importer.open(path.getAbsolutePath(), 0);
			OMEXMLMetadata imgProps = importer.getOMEXMLMetaData();
			int imgSizeX = imgProps.getPixelsSizeX(0).getValue();
			int imgSizeY = imgProps.getPixelsSizeY(0).getValue();

			return new Dimension(imgSizeX, imgSizeY);

		} catch (UnsupportedFormatException | IOException e1) {
			throw e1;
		} finally {
			try {
				importer.close();
			} catch (IOException e2) {
				throw e2;
			}
		}
	}

	/**
	 * Retrieves the amount of channels of the image in the specified file path.
	 *
	 * @param path
	 *          Path of the file.
	 * @return The amount of color channels of the image file.
	 * @throws UnsupportedFormatException
	 *           If the file can't be read.
	 * @throws IOException
	 *           If the file doesn't exist.
	 */
	public static int getImageChannelCount(File path) throws UnsupportedFormatException, IOException {
		LociImporterPlugin importer = new LociImporterPlugin();
		try {
			importer.open(path.getAbsolutePath(), 0);
			OMEXMLMetadata imgProps = importer.getOMEXMLMetaData();
			return imgProps.getPixelsSizeC(0).getValue();

		} catch (UnsupportedFormatException | IOException e1) {
			throw e1;
		} finally {
			try {
				importer.close();
			} catch (IOException e2) {
				throw e2;
			}
		}
	}

	/**
	 * Retrieves the data type of the image specified the the given path.
	 * 
	 * @param path
	 *          Path of the image
	 * @return The data type of the image specified by the given path.
	 * @throws UnsupportedFormatException
	 *           If the file can't be read.
	 * @throws IOException
	 *           If the file doesn't exist.
	 */
	public static DataType getImageDataType(File path) throws UnsupportedFormatException, IOException {
		LociImporterPlugin importer = new LociImporterPlugin();
		try {
			importer.open(path.getAbsolutePath(), 0);
			OMEXMLMetadata imgProps = importer.getOMEXMLMetaData();
			return DataType.getDataTypeFromPixelType(imgProps.getPixelsType(0));

		} catch (UnsupportedFormatException | IOException e1) {
			throw e1;
		} finally {
			try {
				importer.close();
			} catch (IOException e2) {
				throw e2;
			}
		}
	}

	/**
	 * Retrieves the image data size in bytes of the image at the given path. This
	 * is taking into account the image size in X, Y, channel count and the data
	 * type.
	 * 
	 * @param path
	 *          Path of the file
	 * @return The size of the image data in bytes (not the file size).
	 * @throws UnsupportedFormatException
	 *           If the file can't be read.
	 * @throws IOException
	 *           If the file doesn't exist.
	 */
	public static long getImageDataSize(File path) throws UnsupportedFormatException, IOException {
		LociImporterPlugin importer = new LociImporterPlugin();
		try {
			importer.open(path.getAbsolutePath(), 0);
			OMEXMLMetadata imgProps = importer.getOMEXMLMetaData();
			long sizeX = imgProps.getPixelsSizeX(0).getValue();
			long sizeY = imgProps.getPixelsSizeY(0).getValue();
			long sizeC = imgProps.getPixelsSizeC(0).getValue();
			DataType type = DataType.getDataTypeFromPixelType(imgProps.getPixelsType(0));

			long bytesPerPixel = type.getSize() * sizeC;
			return sizeX * sizeY * bytesPerPixel;
		} catch (UnsupportedFormatException | IOException e1) {
			throw e1;
		} finally {
			try {
				importer.close();
			} catch (IOException e2) {
				throw e2;
			}
		}
	}

	/**
	 * Retrieves the image size in {@link Length} format.
	 * 
	 * @param path
	 *          Path of the file
	 * @return The dimension of the image in the specified file.
	 * @throws UnsupportedFormatException
	 *           If the file can't be read.
	 * @throws IOException
	 *           If the file doesn't exist.
	 */
	public static Length[] getImagePixelSize(File path) throws UnsupportedFormatException, IOException {
		LociImporterPlugin importer = new LociImporterPlugin();
		try {
			importer.open(path.getAbsolutePath(), 0);
			try {
				OMEXMLMetadata imgProps = importer.getOMEXMLMetaData();
				Length[] pixelSize = new Length[3];
				pixelSize[0] = imgProps.getPixelsPhysicalSizeX(0);
				if (pixelSize[0] == null) {
					pixelSize[0] = new Length(new Double(1), UNITS.MICROMETER);
				}
				pixelSize[1] = imgProps.getPixelsPhysicalSizeY(0);
				if (pixelSize[1] == null) {
					pixelSize[1] = new Length(new Double(1), UNITS.MICROMETER);
				}
				pixelSize[2] = imgProps.getPixelsPhysicalSizeZ(0);
				if (pixelSize[2] == null) {
					pixelSize[2] = new Length(new Double(1), UNITS.MICROMETER);
				}
				return pixelSize;
			} finally {
				importer.close();
			}
		} catch (UnsupportedFormatException | IOException e) {
			throw e;
		}
	}

	/**
	 * Retrieves the metadata stored in a given file.
	 * 
	 * @param path
	 *          File to read.
	 * @return Metadata of the file.
	 * @throws UnsupportedFormatException
	 *           If the format of the file is not compatible.
	 * @throws IOException
	 *           If the file cannot read appropriately.
	 */
	public static OMEXMLMetadata getImageMetadata(File path) throws UnsupportedFormatException, IOException {
		LociImporterPlugin importer = new LociImporterPlugin();
		importer.open(path.getAbsolutePath(), 0);
		try {
			return importer.getOMEXMLMetaData();
		} finally {
			importer.close();
		}
	}

	/**
	 * Retrieves the name of the image stored in the given file.
	 * 
	 * @param path
	 *          File to read.
	 * @return Name of the image.
	 * @throws UnsupportedFormatException
	 *           If the image format is not compatible.
	 * @throws IOException
	 *           If the file cannot be read.
	 */
	public static String getImageName(File path) throws UnsupportedFormatException, IOException {
		LociImporterPlugin importer = new LociImporterPlugin();
		importer.open(path.getAbsolutePath(), 0);
		try {
			OMEXMLMetadata imgProps = importer.getOMEXMLMetaData();
			return MetaDataUtil.getName(imgProps, 0);
		} finally {
			importer.close();
		}
	}

	/**
	 * Finds the resolution level that fits an image inside a given dimension. The
	 * image is stored in the given file.
	 * 
	 * @param file
	 *          Image file.
	 * @param targetDimension
	 *          Dimension to fit.
	 * @return Resolution level that fits inside the given dimension.
	 * @throws IOException
	 *           If the image file cannot be read.
	 * @throws UnsupportedFormatException
	 *           If the image format is not compatible.
	 */
	public static int getResolutionLevel(File file, Dimension targetDimension)
			throws IOException, UnsupportedFormatException {
		Dimension dims = getImageDimension(file);
		int resolution = 0;
		while (dims.width > targetDimension.width || dims.height > targetDimension.height) {
			resolution++;
			dims.width /= 2;
			dims.height /= 2;
		}
		return resolution;
	}
}
