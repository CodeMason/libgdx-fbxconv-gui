package asf.modelpreview.desktop;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.plaf.basic.BasicFileChooserUI;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.net.URI;
import java.util.List;

/**
 * Created by Danny on 10/30/2014.
 */
public class FileChooserSideBar {

	final DesktopLauncher desktopLauncher;
	JFileChooser fileChooser;
	BasicFileFilter[] fileFilters;
	//private BooleanConfigPanel alwaysConvert;
	private BooleanConfigPanel automaticPreviewBox;
	private JButton previewFileButton, convertButton;

	private PropertyChangeListener selectedFilePropertyChange;

	public FileChooserSideBar(final DesktopLauncher desktopLauncher, JPanel parentPanel) {

		JPanel chooserBasePanel = new JPanel(new BorderLayout());
		parentPanel.add(chooserBasePanel);


		this.desktopLauncher = desktopLauncher;
		fileChooser = new JFileChooser();
		chooserBasePanel.add(fileChooser, BorderLayout.CENTER);
		fileChooser.setDialogTitle("Choose Assets Directory");
		fileChooser.setDialogType(JFileChooser.CUSTOM_DIALOG);
		fileChooser.setControlButtonsAreShown(false);
		fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		fileChooser.setMultiSelectionEnabled(true);

		fileChooser.setFileHidingEnabled(true);
		fileChooser.setAcceptAllFileFilterUsed(false);
		if (fileChooser.getUI() instanceof BasicFileChooserUI) {
			BasicFileChooserUI ui = (BasicFileChooserUI) fileChooser.getUI();
			ui.getNewFolderAction().setEnabled(false);
		}

		fileFilters = new BasicFileFilter[]{
			new BasicFileFilter("Autodesk *.fbx", ".fbx"),
			new BasicFileFilter("Wavefront *.obj", ".obj"),
			new BasicFileFilter("Collada *.dae", ".dae"),
			new BasicFileFilter("LibGDX 3D Binary *.g3db", ".g3db"),
			new BasicFileFilter("LibGDX 3D Json *.g3dj", ".g3dj"),
			new BasicFileFilter("All Compatible LibGDX Files", ".obj", ".fbx", ".dae", ".g3db", ".g3dj")
		};
		for (BasicFileFilter fileFilter : fileFilters) {
			fileChooser.addChoosableFileFilter(fileFilter);
		}
		int prefFileFilter = desktopLauncher.prefs.getInt(DesktopLauncher.I_fileFilter, fileFilters.length - 1);
		if (prefFileFilter < 0 || prefFileFilter >= fileFilters.length) {
			prefFileFilter = fileFilters.length - 1;
		}
		fileChooser.setFileFilter(fileFilters[prefFileFilter]);
		fileChooser.addPropertyChangeListener("fileFilterChanged", new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				for (int i = 0; i < fileFilters.length; i++) {
					if (fileFilters[i] == evt.getNewValue()) {
						desktopLauncher.prefs.putInt(DesktopLauncher.I_fileFilter, i);
						break;
					}
				}
				//onSelectedFileChanged();
			}
		});

		String loc = desktopLauncher.prefs.get(DesktopLauncher.S_folderLocation, null);
		if (loc != null)
			fileChooser.setCurrentDirectory(new File(loc));

		fileChooser.addPropertyChangeListener("directoryChanged", new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				File newVal = (File) evt.getNewValue();
				desktopLauncher.prefs.put(DesktopLauncher.S_folderLocation, newVal.getAbsolutePath());
			}
		});

		selectedFilePropertyChange = new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
					onSelectedFileChanged();
			}
		};
		//fileChooser.addPropertyChangeListener("SelectedFileChangedProperty", selectedFilePropertyChange);
		fileChooser.addPropertyChangeListener("SelectedFilesChangedProperty", selectedFilePropertyChange);

		// File preview options
		{
			JPanel westSouthPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
			chooserBasePanel.add(westSouthPanel, BorderLayout.SOUTH);

			/*
			alwaysConvert= new BooleanConfigPanel(desktopLauncher, westSouthPanel, "Convert on Drag n' Drop", DesktopLauncher.B_alwaysConvert, true){
                                @Override
                                protected void onChange() {

                                }
                        };
                        alwaysConvert.checkBox.setToolTipText("<html>Check this to convert the source file instead of previewing it first when dragging<br>(Preview is still shown after converting)</html>");
			*/

			automaticPreviewBox = new BooleanConfigPanel(desktopLauncher, westSouthPanel, "Automatic Preview", DesktopLauncher.B_automaticPreview, true) {
				@Override
				protected void onChange() {
					if (isSelected())
						desktopLauncher.displaySelectedFiles(true);
				}
			};


			previewFileButton = new JButton("Preview File");
			westSouthPanel.add(previewFileButton);
			previewFileButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent e) {
					desktopLauncher.displaySelectedFiles(true);
				}
			});


			convertButton = new JButton("Convert");
			westSouthPanel.add(convertButton);
			convertButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					desktopLauncher.displaySelectedFiles(false);
				}
			});

		}


	}

	public void setSelectedFile(File[] files) {
		// I dont use the property change listener here because if you
		// drag n drop the same file twice, it wont fire the
		// property change listener..
		fileChooser.removePropertyChangeListener("SelectedFilesChangedProperty", selectedFilePropertyChange);
		fileChooser.setSelectedFiles(files);
		onSelectedFileChanged();
		fileChooser.addPropertyChangeListener("SelectedFilesChangedProperty", selectedFilePropertyChange);

	}

	private void onSelectedFileChanged() {
		System.out.println("onSelectedFileChanged");
		refreshConvertButtonText();
		if (automaticPreviewBox.isSelected())
			desktopLauncher.displaySelectedFiles(true);
	}

	protected void refreshConvertButtonText() {
		if (convertButton == null) {
			return;
		}
		File[] selectedFiles = fileChooser.getSelectedFiles();
		if(selectedFiles.length == 0){
			convertButton.setText("Choose a file to convert");
			convertButton.setEnabled(false);
		}else if(selectedFiles.length == 1){
			File sf = fileChooser.getSelectedFile();
			if (isFileCanBeConverted(sf)) {
				String ext = desktopLauncher.outputFileTypeBox.getValue().toLowerCase();
				String val = DesktopLauncher.stripExtension(sf.getName()) + "." + ext;
				convertButton.setText("Convert "+sf.getName()+" to: " + val);
				convertButton.setEnabled(true);
			} else {
				convertButton.setText("Choose a file to convert");
				convertButton.setEnabled(false);
			}
		}else{
			String ext = desktopLauncher.outputFileTypeBox.getValue().toLowerCase();
			convertButton.setText("Batch Convert "+selectedFiles.length+" files to ."+ext);
			convertButton.setEnabled(true);
		}


	}

	public boolean isAutomaticPreview() {
		return automaticPreviewBox.isSelected();
	}

	public File getSelectedFile() {
		return fileChooser.getSelectedFile();
	}

	public File[] getSelectedFilesToConvert(){
		return fileChooser.getSelectedFiles();
	}

	/**
	 * returns true if this file is the chosen input file format
	 * and can be converted (ie is not g3db or g3dj)
	 *
	 * @param file
	 * @return
	 */
	public boolean isFileCanBeConverted(File file) {
		if (file == null || file.isDirectory())
			return false;

		String name = file.getName().toLowerCase();
		if(name.endsWith(".g3db") || name.endsWith(".g3dj"))
			return false;

		return fileChooser.getFileFilter().accept(file);
	}

	public void refreshFileChooserList() {
		fileChooser.rescanCurrentDirectory();
		//((javax.swing.plaf.basic.BasicDirectoryModel)list.getModel()).fireContentsChanged();
	}


}

