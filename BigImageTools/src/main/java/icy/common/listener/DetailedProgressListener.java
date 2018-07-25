package icy.common.listener;

/**
 * Progress notification listener enriched with optional comments and optional
 * data.
 * 
 * @author Daniel Felipe Gonzalez Obando
 */
@FunctionalInterface
public interface DetailedProgressListener {
	public boolean notifyProgress(double progress, String message, Object data);
}
