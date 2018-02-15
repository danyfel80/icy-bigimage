package plugins.danyfel80.bigimage.io;

import java.io.File;
import java.nio.file.Paths;

import algorithms.danyfel80.io.image.big.BigImageExporter;
import algorithms.danyfel80.io.image.tileprovider.IcyBufferedImageTileProvider;
import icy.file.FileUtil;
import icy.image.IcyBufferedImage;
import icy.sequence.Sequence;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import ome.xml.meta.IMetadata;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzStoppable;
import plugins.adufour.ezplug.EzVarFile;
import plugins.adufour.ezplug.EzVarSequence;

/**
 * This class allows to save a big image using tiles.
 * 
 * @author Daniel Felipe Gonzalez Obando
 */
public class SaveBigImage extends EzPlug implements EzStoppable {

	private EzVarSequence inSeq;
	private EzVarFile inFile;

	@Override
	protected void initialize() {
		inSeq = new EzVarSequence("Sequence");
		inFile = new EzVarFile("File", "", ".ome.tif");
		addEzComponent(inSeq);
		addEzComponent(inFile);
	}

	@Override
	protected void execute() {
		Sequence seq = inSeq.getValue();
		File file = inFile.getValue();
		file = Paths.get(FileUtil.setExtension(file.getAbsolutePath(), ".ome.tif")).toFile();

		IcyBufferedImage image = seq.getFirstImage();
		IMetadata metadata;
		try {
			metadata = BigImageExporter.createMetadata(image.getSizeX(), image.getSizeY(), image.getSizeC(),
					image.getDataType_());
		} catch (DependencyException | ServiceException e) {
			throw new RuntimeException(e);
		}
		metadata.setImageName(seq.getName(), 0);
		metadata.setPixelsPhysicalSizeX(seq.getOMEXMLMetadata().getPixelsPhysicalSizeX(0), 0);
		metadata.setPixelsPhysicalSizeY(seq.getOMEXMLMetadata().getPixelsPhysicalSizeY(0), 0);
		metadata.setPixelsPhysicalSizeZ(seq.getOMEXMLMetadata().getPixelsPhysicalSizeZ(0), 0);

		try (BigImageExporter exporter = new BigImageExporter(file, metadata)) {
			exporter.setProgressListener((double progress, String message, Object data) -> {
				this.getUI().setProgressBarMessage(message);
				this.getUI().setProgressBarValue(progress);
				return false;
			});
			exporter.write(new IcyBufferedImageTileProvider(image));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void clean() {
	}

}
