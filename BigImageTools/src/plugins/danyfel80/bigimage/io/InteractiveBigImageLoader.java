package plugins.danyfel80.bigimage.io;

import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.swing.JButton;

import algorithms.danyfel80.bigimage.io.BigImageReader;
import algorithms.danyfel80.bigimage.io.BigImageUtil;
import icy.common.exception.UnsupportedFormatException;
import icy.common.listener.RichProgressListener;
import icy.gui.dialog.MessageDialog;
import icy.roi.ROI;
import icy.sequence.Sequence;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzVarFile;
import plugins.kernel.roi.roi2d.ROI2DRectangle;

public class InteractiveBigImageLoader extends EzPlug {

	private EzVarFile inFile;
	private JButton loadFileButton;
	private Sequence loadedTile;

	@Override
	protected void initialize() {
		inFile = new EzVarFile("Input File", null);
		loadFileButton = new JButton("Load preview");
		loadFileButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Thread tileThread = new Thread(new Runnable() {

					@Override
					public void run() {
						File path = inFile.getValue();
						int maxWidth = 2000;
						int maxHeight = 2000;
						try {
							BigImageReader loader = new BigImageReader(path, null, maxWidth, maxHeight,
									new RichProgressListener() {
										@Override
										public boolean notifyProgress(double position, double length, String message,
												Object data) {
											if (InteractiveBigImageLoader.this.getUI() != null) {
												InteractiveBigImageLoader.this.getUI()
														.setProgressBarValue(position / length);
												InteractiveBigImageLoader.this.getUI().setProgressBarMessage(message
														+ "... (tile " + (int) position + "/" + (int) length + ")");
											}
											return true;
										}
									});

							Thread loaderThread = new Thread(loader);
							loaderThread.start();
							loaderThread.join();
							if (InteractiveBigImageLoader.this.getUI() != null) {
								InteractiveBigImageLoader.this.getUI().setProgressBarValue(0);
								InteractiveBigImageLoader.this.getUI()
										.setProgressBarMessage("Please select a rectangle to load more details.");
							}
							loadedTile = loader.getSequence();
							addSequence(loadedTile);
						} catch (IllegalArgumentException | UnsupportedFormatException | IOException e) {
							e.printStackTrace();
							return;
						} catch (InterruptedException e1) {
							System.out.println("Plugin execution interrupted.");
						}

					}
				});
				tileThread.start();
			}
		});

		addEzComponent(inFile);
		addComponent(loadFileButton);
	}

	@Override
	protected void execute() {
		if (loadedTile != null && !loadedTile.isEmpty()) {
			List<ROI> rects = loadedTile.getROIs(ROI2DRectangle.class);
			if (!rects.isEmpty()) {

				File path = inFile.getValue();
				int maxWidth = 2000;
				int maxHeight = 2000;
				try {
					Rectangle rectToLoad = new Rectangle(((ROI2DRectangle) rects.get(0)).getBounds());
					double scale = BigImageUtil.getImageDimension(path).getWidth() / (double) loadedTile.getWidth();
					rectToLoad.setBounds((int) (rectToLoad.getX() * scale), (int) (rectToLoad.getY() * scale),
							(int) (rectToLoad.getWidth() * scale), (int) (rectToLoad.getHeight() * scale));
					BigImageReader loader = new BigImageReader(path, rectToLoad, maxWidth, maxHeight,
							new RichProgressListener() {
								@Override
								public boolean notifyProgress(double position, double length, String message,
										Object data) {
									if (InteractiveBigImageLoader.this.getUI() != null) {
										InteractiveBigImageLoader.this.getUI().setProgressBarValue(position / length);
										InteractiveBigImageLoader.this.getUI().setProgressBarMessage(
												message + "... (tile " + (int) position + "/" + (int) length + ")");
									}
									return true;
								}
							});

					Thread loaderThread = new Thread(loader);
					loaderThread.start();
					loaderThread.join();
					Sequence loadedImage = loader.getSequence();
					addSequence(loadedImage);
					if (InteractiveBigImageLoader.this.getUI() != null) {
						InteractiveBigImageLoader.this.getUI().setProgressBarValue(0);
					}
				} catch (IllegalArgumentException | UnsupportedFormatException | IOException e) {
					e.printStackTrace();
					return;
				} catch (InterruptedException e1) {
					System.out.println("Plugin execution interrupted.");
				}
			} else {
				MessageDialog.showDialog("No selection has been made", MessageDialog.ERROR_MESSAGE);
			}
		} else {
			MessageDialog.showDialog("No image preview has been opened", MessageDialog.ERROR_MESSAGE);
		}
	}

	@Override
	public void clean() {
	}

}
