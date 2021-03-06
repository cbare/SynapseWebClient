package org.sagebionetworks.web.client.widget.entity;

import org.gwtbootstrap3.client.shared.event.ModalShownEvent;
import org.gwtbootstrap3.client.shared.event.ModalShownHandler;
import org.gwtbootstrap3.client.ui.Alert;
import org.gwtbootstrap3.client.ui.Button;
import org.gwtbootstrap3.client.ui.FormLabel;
import org.gwtbootstrap3.client.ui.Modal;
import org.gwtbootstrap3.client.ui.TextBox;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

/**
 * A basic implementation of EntityNameModalView with zero business logic.
 * 
 * @author jhill
 *
 */
public class EntityNameModalViewImpl implements EntityNameModalView {
	
	public interface Binder extends UiBinder<Modal, EntityNameModalViewImpl> {}
	
	@UiField
	Modal modal;
	@UiField
	FormLabel nameLabel;
	@UiField
	TextBox nameField;
	@UiField
	Alert alert;
	@UiField
	Button primaryButton;
	
	Modal createTableModal;

	@Inject
	public EntityNameModalViewImpl(Binder binder){
		createTableModal = binder.createAndBindUi(this);
		modal.addShownHandler(new ModalShownHandler() {
			
			@Override
			public void onShown(ModalShownEvent evt) {
				nameField.setFocus(true);
				nameField.selectAll();
			}
		});
	}
	
	@Override
	public Widget asWidget() {
		return createTableModal;
	}

	@Override
	public String getName() {
		return nameField.getText();
	}

	@Override
	public void showError(String error) {
		alert.setVisible(true);
		alert.setText(error);
	}

	@Override
	public void setPresenter(final Presenter presenter) {
		this.primaryButton.addClickHandler(new ClickHandler() {
			@Override
			public void onClick(ClickEvent arg0) {
				presenter.onPrimary();
			}
		});
		this.nameField.addKeyDownHandler(new KeyDownHandler() {
			@Override
			public void onKeyDown(KeyDownEvent event) {
				if(KeyCodes.KEY_ENTER == event.getNativeKeyCode()){
					presenter.onPrimary();
				}
			}
		});
	}

	@Override
	public void hide() {
		createTableModal.hide();
	}

	@Override
	public void show() {
		createTableModal.show();
		nameField.setFocus(true);
	}

	@Override
	public void clear() {
		this.primaryButton.state().reset();
		this.alert.setVisible(false);
		this.nameField.clear();
	}

	@Override
	public void setLoading(boolean isLoading) {
		if(isLoading){
			this.primaryButton.state().loading();
		}else{
			this.primaryButton.state().reset();
		}	
	}

	@Override
	public void configure(String title, String label, String buttonText, String name) {
		this.modal.setTitle(title);
		this.nameLabel.setText(label);
		this.primaryButton.setText(buttonText);
		this.nameField.setText(name);
	}

}
