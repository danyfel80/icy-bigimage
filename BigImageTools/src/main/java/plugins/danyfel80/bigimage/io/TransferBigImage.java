package plugins.danyfel80.bigimage.io;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;

import algorithms.danyfel80.io.sequence.large.LargeSequenceExporter;
import algorithms.danyfel80.io.sequence.tileprovider.LargeSequenceTileProvider;
import icy.common.exception.UnsupportedFormatException;
import icy.common.listener.DetailedProgressListener;
import icy.sequence.MetaDataUtil;
import icy.system.IcyHandledException;
import loci.formats.FormatException;
import ome.xml.meta.OMEXMLMetadata;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzVarFile;
import plugins.kernel.importer.LociImporterPlugin;

public class TransferBigImage extends EzPlug {

	EzVarFile inputFileVar;
	EzVarFile outputFileVar;

	private Path inputFilePath;
	private Path outputFilePath;
	private LargeSequenceTileProvider tileProvider;
	private LociImporterPlugin importer;
	private LargeSequenceExporter exporter;
	
	private DetailedProgressListener progressListener;

	@Override
	protected void initialize() {
		inputFileVar = new EzVarFile("Input File", null);
		outputFileVar = new EzVarFile("Output File", "", ".ome.tif");

		addEzComponent(inputFileVar);
		addEzComponent(outputFileVar);
	}

	@Override
	protected void execute() {
		retrieveParameters();
		createTileProvider();
		try {
			createImageExporter();
			try {
				transfer();
			} finally {
				closeExporter();
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new IcyHandledException(e);
		} finally {
			try {
				importer.close();
			} catch (IOException e) {
				e.printStackTrace();
				throw new IcyHandledException(e);
			}
		}
	}

	private void retrieveParameters() {
		inputFilePath = inputFileVar.getValue(true).toPath();
		outputFilePath = outputFileVar.getValue(true).toPath();
		PathMatcher extensionMatcher = FileSystems.getDefault().getPathMatcher("glob:*.ome.tiff");
		if (!extensionMatcher.matches(outputFilePath.getFileName())) {
			outputFilePath = outputFilePath.resolveSibling(outputFilePath.getFileName() + ".ome.tiff");
		}
	}

	private void createTileProvider() {
		tileProvider = new LargeSequenceTileProvider();
		getInputImageImporter();
		tileProvider.setImporter(importer);
	}

	private void getInputImageImporter() {
		importer = new LociImporterPlugin();
		try {
			importer.open(inputFilePath.toString(), LociImporterPlugin.FLAG_METADATA_ALL);
		} catch (UnsupportedFormatException | IOException e) {
			e.printStackTrace();
			throw new IcyHandledException("Could not open input file", e);
		}
	}

	private void createImageExporter() throws UnsupportedFormatException, IOException {
		exporter = new LargeSequenceExporter();
		exporter.setOutputFilePath(outputFilePath);
		OMEXMLMetadata inputMetadata = importer.getOMEXMLMetaData();
		exporter.setOutputImageMetadata(LargeSequenceExporter.createMetadata(MetaDataUtil.getSizeX(inputMetadata, 0),
				MetaDataUtil.getSizeY(inputMetadata, 0), MetaDataUtil.getSizeC(inputMetadata, 0),
				MetaDataUtil.getDataType(inputMetadata, 0)));
		exporter.setTileProvider(tileProvider);
		exporter.TILE_SIZE.setSize(importer.getTileWidth(0), importer.getTileHeight(0));
		exporter.addProgressListener(getProgressListener());
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

	private void transfer() throws InterruptedException, IOException, FormatException {
		exporter.write();
	}

	private void closeExporter() throws Exception {
		exporter.close();
	}

	@Override
	public void clean() {
	}

}
