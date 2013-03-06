package org.sagebionetworks.web.client.widget.entity;

import com.google.gwt.user.client.ui.IsWidget;

public interface PreviewWidgetView extends IsWidget{
	/**
	 * Set the presenter.
	 * @param presenter
	 */
	public void setPresenter(Presenter presenter);

	public void setImagePreview(String fullFileUrl, String previewUrl);
	public void setCodePreview(String code);
	public void setBlockQuotePreview(String text);
	public void showErrorMessage(String message);
	public void reset();
	
	/**
	 * Presenter interface
	 */
	public interface Presenter {
	}


}
