package plugins.danyfel80.bigimage.segmentation;

import algorithms.danyfel80.bigimage.segmentation.BigImageThresholder;
import icy.common.listener.ProgressListener;
import plugins.adufour.blocks.lang.Block;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzStoppable;
import plugins.adufour.ezplug.EzVarBoolean;
import plugins.adufour.ezplug.EzVarFile;
import plugins.adufour.ezplug.EzVarInteger;

/**
 * This plugin performs a thresholding on a big image and stocks the result on
 * the hard disk.
 * 
 * @author Daniel Felipe Gonzalez Obando
 */
public class ThresholdBigImage extends EzPlug implements Block, EzStoppable {

	private EzVarFile inInPathVar = new EzVarFile("Input file", "");
	private EzVarInteger inClsVar = new EzVarInteger("Classes", 2, 6, 1);
	private EzVarFile inOutPathVar = new EzVarFile("Output file", "");

	private EzVarBoolean outSuccessVar = new EzVarBoolean("Success", true);

	private BigImageThresholder thresholder;
	private Thread threshThread;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * plugins.adufour.blocks.lang.Block#declareInput(plugins.adufour.blocks.util.
	 * VarList)
	 */
	@Override
	public void declareInput(VarList inputMap) {
		inputMap.add(inInPathVar.name, inInPathVar.getVariable());
		inputMap.add(inClsVar.name, inClsVar.getVariable());
		inputMap.add(inOutPathVar.name, inOutPathVar.getVariable());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * plugins.adufour.blocks.lang.Block#declareOutput(plugins.adufour.blocks.util
	 * .VarList)
	 */
	@Override
	public void declareOutput(VarList outputMap) {
		outputMap.add(outSuccessVar.name, outSuccessVar.getVariable());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see plugins.adufour.ezplug.EzPlug#initialize()
	 */
	@Override
	protected void initialize() {
		addEzComponent(inInPathVar);
		addEzComponent(inClsVar);
		addEzComponent(inOutPathVar);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see plugins.adufour.ezplug.EzPlug#execute()
	 */
	@Override
	protected void execute() {
		long startTime = System.nanoTime();
		try {
			this.thresholder = new BigImageThresholder(inInPathVar.getValue(), inClsVar.getValue(), inOutPathVar.getValue(),
			    new ProgressListener() {

				    @Override
				    public boolean notifyProgress(double position, double length) {
					    if (ThresholdBigImage.this.getUI() != null) {
						    ThresholdBigImage.this.getUI().setProgressBarValue(position / length);
						    ThresholdBigImage.this.getUI()
			              .setProgressBarMessage(String.format("Thresholding... %.02f%%", position * 100.0 / length));
					    }
					    return false;
				    }
			    });
		} catch (IllegalArgumentException e) {
			this.outSuccessVar.setValue(false);
			return;
		}

		this.threshThread = new Thread(thresholder);
		threshThread.start();
		try {
			threshThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
			this.outSuccessVar.setValue(false);
			return;
		} finally {
			this.thresholder = null;
			this.threshThread = null;
		}
		long endTime = System.nanoTime();
		System.out.println("Thresholding finished: " + ((endTime - startTime) / 1000000) + "msecs.");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see plugins.adufour.ezplug.EzPlug#clean()
	 */
	@Override
	public void clean() {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see plugins.adufour.ezplug.EzPlug#stopExecution()
	 */
	@Override
	public void stopExecution() {
		this.thresholder.interrupt();
		super.stopExecution();
	}

}
