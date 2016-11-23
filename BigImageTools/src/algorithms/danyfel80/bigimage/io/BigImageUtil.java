/**
 * 
 */
package algorithms.danyfel80.bigimage.io;

import java.awt.Dimension;
import java.io.File;
import java.io.IOException;

import icy.common.exception.UnsupportedFormatException;
import icy.type.DataType;
import icy.type.dimension.Dimension3D;
import loci.formats.ome.OMEXMLMetadataImpl;
import ome.units.quantity.Length;
import plugins.kernel.importer.LociImporterPlugin;

/**
 * This is an utilitary class to provide information about big image files.
 * 
 * @author Daniel Felipe Gonzalez Obando
 */
public class BigImageUtil {
	/**
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
			OMEXMLMetadataImpl imgProps = importer.getMetaData();
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
			OMEXMLMetadataImpl imgProps = importer.getMetaData();
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
			OMEXMLMetadataImpl imgProps = importer.getMetaData();
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
			OMEXMLMetadataImpl imgProps = importer.getMetaData();
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
	
	public static Length[] getImagePixelSize(File path) throws UnsupportedFormatException, IOException {
		LociImporterPlugin importer = new LociImporterPlugin();
		try {
			importer.open(path.getAbsolutePath(), 0);
			OMEXMLMetadataImpl imgProps = importer.getMetaData();
			Length[] pixelSize = new Length[3];
			pixelSize[0] = imgProps.getPixelsPhysicalSizeX(0);
			pixelSize[1] = imgProps.getPixelsPhysicalSizeY(0);
			pixelSize[2] = imgProps.getPixelsPhysicalSizeZ(0);
			return pixelSize;
		} finally {
			importer.close();
		}
	}
}
