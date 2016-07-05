package plugins.danyfel80.bigimage.blocks.lang;

import java.util.List;

import plugins.adufour.blocks.lang.Loop;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.vars.lang.Var;
import plugins.adufour.vars.lang.VarInteger;

/**
 * Particular kind of loop that will go through all image scales until a desired
 * scale. Scales are defined by s from 0 to n and the input image is tiled using
 * the formula scalingFactor = 1 / pow(2, s). If necessary the tiles can be
 * downsampled to a fixed maximum size to handle memory limitations.
 * 
 * @author Daniel Felipe Gonzalez Obando
 */
public class ImagePyramidLoop extends Loop {
	// ---- Private members ----
	// scale limits
	private VarInteger startScale;
	private VarInteger endScale;
	private VarInteger currentScaleTileNum;

	// scale indexes
	private VarInteger currentScale;
	private VarInteger currentTile;

	// down-sampling variables
	private VarInteger maxImageWidth;
	private VarInteger maxImageHeight;

	// ---- Getter Methods ----
	/**
	 * @return The first scale to process
	 */
	public VarInteger getStartScale() {
		return startScale;
	}

	/**
	 * @return The last scale to process
	 */
	public VarInteger getEndScale() {
		return endScale;
	}

	/**
	 * @return The current scale's tile number
	 */
	public VarInteger getCurrentScaleTileNum() {
		return currentScaleTileNum;
	}

	/**
	 * @return The current scale
	 */
	public VarInteger getCurrentScale() {
		return currentScale;
	}

	/**
	 * @return The current tile
	 */
	public VarInteger getCurrentTile() {
		return currentTile;
	}

	/**
	 * @return The maximum processed tile image width
	 */
	public VarInteger getMaxImageWidth() {
		return maxImageWidth;
	}

	/**
	 * @return The maximum processed image width
	 */
	public VarInteger getMaxImageHeight() {
		return maxImageHeight;
	}

	// ---- Overridden methods ----
	
	/* (non-Javadoc)
	 * @see plugins.adufour.blocks.lang.Loop#initializeLoop()
	 */
	@Override
	public void initializeLoop() {
		// TODO Auto-generated method stub
		super.initializeLoop();
	}

	/* (non-Javadoc)
	 * @see plugins.adufour.blocks.lang.Loop#beforeIteration()
	 */
	@Override
	public void beforeIteration() {
		// TODO Auto-generated method stub
		super.beforeIteration();
	}

	/* (non-Javadoc)
	 * @see plugins.adufour.blocks.lang.Loop#afterIteration()
	 */
	@Override
	public void afterIteration() {
		// TODO Auto-generated method stub
		super.afterIteration();
	}

	/* (non-Javadoc)
	 * @see plugins.adufour.blocks.lang.Loop#isStopConditionReached()
	 */
	@Override
	public boolean isStopConditionReached() {
		// TODO Auto-generated method stub
		return super.isStopConditionReached();
	}

	/* (non-Javadoc)
	 * @see plugins.adufour.blocks.lang.WorkFlow#declareInput(plugins.adufour.blocks.util.VarList)
	 */
	@Override
	public void declareInput(VarList inputMap) {
		super.declareInput(inputMap);
		
		inputMap.add("First scale ", startScale = new VarInteger("First scale", 0));
    inputMap.add("Last scale", endScale = new VarInteger("Last scale", 0));
    inputMap.add("Max image width", endScale = new VarInteger("Max image width", 1000));
    inputMap.add("Max image height", endScale = new VarInteger("Max image height", 1000));
    
	}

	/* (non-Javadoc)
	 * @see plugins.adufour.blocks.lang.Loop#declareOutput(plugins.adufour.blocks.util.VarList)
	 */
	@Override
	public void declareOutput(VarList outputMap) {
		// TODO Auto-generated method stub
		super.declareOutput(outputMap);
	}

	/* (non-Javadoc)
	 * @see plugins.adufour.blocks.lang.Loop#declareLoopVariables(java.util.List)
	 */
	@Override
	public void declareLoopVariables(List<Var<?>> loopVariables) {
		// TODO Auto-generated method stub
		super.declareLoopVariables(loopVariables);
	}
	
	
	
}
