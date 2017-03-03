package plugins.danyfel80.bigimage.blocks.bigimage;

import java.io.IOException;

import algorithms.danyfel80.bigimage.io.BigImageWriter;
import plugins.adufour.blocks.lang.Block;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.vars.lang.Var;

/**
 * Block to close an existing open image writer
 * @author Daniel Felipe Gonzalez Obando
 */
public class CloseImageWriter extends EzPlug implements Block {

	private Var<BigImageWriter> inWriter;
	
	/* (non-Javadoc)
	 * @see plugins.adufour.blocks.lang.Block#declareInput(plugins.adufour.blocks.util.VarList)
	 */
	@Override
	public void declareInput(VarList inputMap) {
		inputMap.add("Writer", inWriter = new Var<BigImageWriter>("Writer", BigImageWriter.class));
	}

	/* (non-Javadoc)
	 * @see plugins.adufour.blocks.lang.Block#declareOutput(plugins.adufour.blocks.util.VarList)
	 */
	@Override
	public void declareOutput(VarList outputMap) {
	}

	/* (non-Javadoc)
	 * @see plugins.adufour.ezplug.EzPlug#clean()
	 */
	@Override
	public void clean() {
	}

	/* (non-Javadoc)
	 * @see plugins.adufour.ezplug.EzPlug#execute()
	 */
	@Override
	protected void execute() {
		try {
			if (inWriter.getValue().isOpen()) {
				inWriter.getValue().closeWriter();
			}
		} catch (IOException e) {
			e.printStackTrace();
			this.stopExecution();
		}
	}

	/* (non-Javadoc)
	 * @see plugins.adufour.ezplug.EzPlug#initialize()
	 */
	@Override
	protected void initialize() {
	}

}
