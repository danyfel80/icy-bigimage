package plugins.danyfel80.bigimage.io;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;

import algorithms.danyfel80.io.sequence.large.LargeSequenceExporter;
import algorithms.danyfel80.io.sequence.large.LargeSequenceExporterException;
import algorithms.danyfel80.io.sequence.tileprovider.IcyBufferedImageTileProvider;
import icy.common.listener.DetailedProgressListener;
import icy.sequence.MetaDataUtil;
import icy.sequence.Sequence;
import icy.system.IcyHandledException;
import loci.formats.ome.OMEXMLMetadata;
import plugins.adufour.blocks.lang.Block;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzStoppable;
import plugins.adufour.ezplug.EzVarFile;
import plugins.adufour.ezplug.EzVarSequence;

/**
 * This class allows to save a big image using tiles.
 * 
 * @author Daniel Felipe Gonzalez Obando
 */
public class SaveBigImage extends EzPlug implements EzStoppable, Block {

	private EzVarSequence sequenceVar;
	private EzVarFile fileVar;

	private Sequence sequence;
	private Path filePath;

	private OMEXMLMetadata metadata;
	private DetailedProgressListener progressListener;

	@Override
	protected void initialize() {
		sequenceVar = new EzVarSequence("Sequence");
		fileVar = new EzVarFile("File", null);
		addEzComponent(sequenceVar);
		addEzComponent(fileVar);
	}

	@Override
	public void declareInput(VarList inputMap) {
		sequenceVar = new EzVarSequence("Sequence");
		fileVar = new EzVarFile("File", null);
		inputMap.add(sequenceVar.name, sequenceVar.getVariable());
		inputMap.add(fileVar.name, fileVar.getVariable());
	}

	@Override
	public void declareOutput(VarList outputMap) {
	}

	@Override
	protected void execute() {
		getParameters();
		exportSequence();
	}

	private void getParameters() {
		sequence = sequenceVar.getValue(true);
		filePath = fileVar.getValue(true).toPath();
		PathMatcher extensionMatcher = FileSystems.getDefault().getPathMatcher("glob:*.ome.tiff");
		if (!extensionMatcher.matches(filePath.getFileName())) {
			filePath = filePath.resolveSibling(filePath.getFileName() + ".ome.tiff");
		}
	}

	private void exportSequence() {
		try (LargeSequenceExporter exporter = new LargeSequenceExporter()) {
			createMetadata();
			exporter.setOutputImageMetadata(metadata);
			exporter.setOutputFilePath(filePath);
			exporter.setTileProvider(new IcyBufferedImageTileProvider(sequence.getFirstImage()));
			if (!isHeadLess())
				exporter.addProgressListener(getProgressListener());

			exporter.write();
		} catch (Exception e) {
			e.printStackTrace();
			throw new IcyHandledException(e);
		} finally {
		}
	}

	private void createMetadata() throws LargeSequenceExporterException {
		metadata = LargeSequenceExporter.createMetadata(sequence.getSizeX(), sequence.getSizeY(), sequence.getSizeC(),
				sequence.getDataType_());

		MetaDataUtil.setName(metadata, 0, sequence.getName());
		MetaDataUtil.setPixelSizeX(metadata, 0, sequence.getPixelSizeX());
		MetaDataUtil.setPixelSizeY(metadata, 0, sequence.getPixelSizeY());

		for (int channel = 0; channel < sequence.getSizeC(); channel++) {
			MetaDataUtil.setPositionX(metadata, 0, 0, 0, channel, sequence.getPositionX());
			MetaDataUtil.setPositionY(metadata, 0, 0, 0, channel, sequence.getPositionY());
		}
	}

	private DetailedProgressListener getProgressListener() {
		if (progressListener == null) {
			progressListener = (double progress, String message, Object data) -> {
				this.getUI().setProgressBarMessage(message);
				this.getUI().setProgressBarValue(progress);
				return false;
			};
		}
		return progressListener;
	}

	@Override
	public void clean() {
	}

}
