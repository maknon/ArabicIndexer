package com.maknoon;

/*
 * Updating for the database/books
 *
 * 'info' file in the zipped BIUF (Database) package:
 *
 * Line 1: Short description
 * Line 2: Exported DB version
 * Next Lines are details of the package
 */
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.util.zip.*;
import javax.swing.filechooser.*;
import java.sql.*;
import java.nio.channels.*;

class Update extends JDialog
{
	private final float currentSystemVersion, currentDBVersion;

	private Thread updateThread;
	static Vector<String> updatingFiles = new Vector<>();

	// Global to make accessible from UpdateTableModel (to disable/enable it)
	static JButton updateButton, pathButton;
	static JCheckBox exportWithBooksCheckBox;

	// To indicate the thread status. Thread.currentThread().isInterrupted() is not working, bug. Check http://bugs.sun.com/view_bug.do?bug_id=6772683.
	//private boolean isInterrupted = false; // Version 1.6, bug is resolved

	// Version 1.7, Global to allow using it inside the bookPathSelection()
	final String[] translations;
	final JLabel exportedBooksLabel;

	Update(final ArabicIndexer AI, final Vector<String> passedFiles)
	{
		super(AI, true);

		translations = ArabicIndexer.StreamConverter(ArabicIndexer.programFolder + "language/Update" + ArabicIndexer.language + ".txt");
		setTitle(translations[22]);
		setResizable(false);
		setLayout(new FlowLayout());

		final JPanel mainPanel = new JPanel(new BorderLayout());
		getContentPane().add(mainPanel);

		final JLabel updateLabel = new JLabel(" ", ArabicIndexer.language == ArabicIndexer.lang.English ? SwingConstants.LEFT : SwingConstants.RIGHT);
		mainPanel.add(updateLabel, BorderLayout.NORTH);

		currentSystemVersion = Float.parseFloat(ArabicIndexer.version);
		currentDBVersion = Float.parseFloat(ArabicIndexer.biuf_version);

		final Vector<UpdatesData> updateDataVector = new Vector<>();
		final Vector<String> updateDescriptionVector = new Vector<>();
		final UpdateTableModel tableModel = new UpdateTableModel(updateDataVector, translations[24], translations[26], translations[27], translations[28]);
		final JTable updateTable = new JTable(tableModel);
		updateTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		final DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
		if (ArabicIndexer.language != ArabicIndexer.lang.English)
			renderer.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

		for (int i = 0; i < tableModel.getColumnCount(); i++)
		{
			final TableColumn column = updateTable.getColumnModel().getColumn(i);
			if (i == UpdateTableModel.DESCRIPTION_COL)
				column.setPreferredWidth(400);

			if (i != UpdateTableModel.INSTALL_COL)
				column.setCellRenderer(renderer);
		}

		final JTextArea descriptionTextArea = new JTextArea();
		descriptionTextArea.setEditable(false);
		descriptionTextArea.setBackground(Color.WHITE);

		updateTable.getSelectionModel().addListSelectionListener(new ListSelectionListener()
		{
			public void valueChanged(ListSelectionEvent e)
			{
				if (!e.getValueIsAdjusting())
				{
					int i = ((ListSelectionModel) e.getSource()).getMaxSelectionIndex();
					if (i != -1)
						descriptionTextArea.setText(updateDescriptionVector.elementAt(/*updateTable.getSelectedRow()*/i));
				}
			}
		});

		updateTable.addMouseListener(new MouseAdapter()
		{
			/*
			public void mousePressed(MouseEvent e)
			{
				descriptionTextArea.setText(updateDescriptionVector.elementAt(updateTable.rowAtPoint(e.getPoint())));
			}
			*/

			// Version 1.5
			public void mouseReleased(MouseEvent m)
			{
				if (updateTable.isEnabled()) // Version 1.6
				{
					// Select All items in the list. it can be done as well as checkbox in the header [http://www.coderanch.com/t/343795/GUI/java/Check-Box-JTable-header]
					//if((m.isPopupTrigger() && ArabicIndexer.isWindows) || ((m.getModifiers() & InputEvent.BUTTON3_MASK) == InputEvent.BUTTON3_MASK && !ArabicIndexer.isWindows))
					if ((m.getModifiers() & InputEvent.BUTTON3_MASK) == InputEvent.BUTTON3_MASK) // Version 1.7
					{
						final JMenuItem selectAllMenuItem = new JMenuItem(translations[35]);
						selectAllMenuItem.addActionListener(new ActionListener()
						{
							public void actionPerformed(ActionEvent e)
							{
								for (int i = 0; i < tableModel.getRowCount(); i++)
									if (!updateDataVector.elementAt(i).disabled)
										tableModel.setValueAt(true, i, UpdateTableModel.INSTALL_COL);

								updateTable.updateUI();
							}
						});

						final JPopupMenu popupMenu = new JPopupMenu();
						popupMenu.add(selectAllMenuItem);
						if (ArabicIndexer.language == ArabicIndexer.lang.English) // Version 1.8
							popupMenu.show(updateTable, m.getX(), m.getY());
						else
						{
							popupMenu.updateUI(); // Version 1.6, for getPreferredSize() to return correct value
							popupMenu.show(updateTable, m.getX() - popupMenu.getPreferredSize().width + 15, m.getY());
							//updateTable.setRowSelectionInterval(updateTable.rowAtPoint(m.getPoint()), updateTable.rowAtPoint(m.getPoint()));
						}
					}
				}
			}
		});

		final JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true, new JScrollPane(updateTable), new JScrollPane(descriptionTextArea));
		mainPanel.setPreferredSize(new Dimension(550, 400));
		splitPane.setResizeWeight(.55D);
		splitPane.setDividerSize(5);
		mainPanel.add(splitPane, BorderLayout.CENTER);

		final JPanel southPanel = new JPanel(new BorderLayout());
		mainPanel.add(southPanel, BorderLayout.SOUTH);

		final JPanel exportWithBooksPanel = new JPanel(new BorderLayout());
		southPanel.add(exportWithBooksPanel, BorderLayout.NORTH);

		exportWithBooksCheckBox = new JCheckBox(translations[14], true);
		exportWithBooksCheckBox.setToolTipText(translations[15]);
		exportWithBooksCheckBox.setEnabled(false);
		exportWithBooksPanel.add(exportWithBooksCheckBox, BorderLayout.NORTH);

		// Important to not fire itemStateChanged twice check:
		// http://www.coderanch.com/t/339842/GUI/java/ComboBox-ItemListener-calling-twice
		// http://stackoverflow.com/questions/330590/why-is-itemstatechanged-on-jcombobox-is-called-twice-when-changed
		exportWithBooksCheckBox.setFocusable(false);

		// Version 1.9, default is changed to allow portable distribution of the library with pdf included.
		exportedBooksLabel = new JLabel("<APP ROOT PATH>/pdf/");

		if (ArabicIndexer.language != ArabicIndexer.lang.English)
		{
			exportWithBooksPanel.add(exportedBooksLabel, BorderLayout.WEST);
			exportWithBooksPanel.add(new JLabel("<HTML><font color=red>" + translations[37] + "</font></html>"), BorderLayout.EAST);
		}
		else
		{
			exportWithBooksPanel.add(exportedBooksLabel, BorderLayout.CENTER);
			exportWithBooksPanel.add(new JLabel("<HTML><font color=red>" + translations[37] + "</font></html>"), BorderLayout.WEST);
		}

		final JPanel choicePanel = new JPanel(new GridLayout(1, 4));
		southPanel.add(choicePanel, BorderLayout.WEST);

		final JButton browserButton = new JButton(translations[29]);
		browserButton.setToolTipText(translations[30]);
		choicePanel.add(browserButton);
		browserButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				JFileChooser fc = null;
				int returnVal = JFileChooser.APPROVE_OPTION;
				if (passedFiles.isEmpty())
				{
					fc = new JFileChooser();
					fc.setMultiSelectionEnabled(true);
					fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
					fc.setFileFilter(new FileNameExtensionFilter(translations[21] + " (biuf)", "biuf"));
					fc.setAcceptAllFileFilterUsed(false);
					if (ArabicIndexer.language != ArabicIndexer.lang.English)
						fc.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
					fc.setDialogTitle(translations[19]);
					returnVal = fc.showOpenDialog(Update.this);
				}

				if (returnVal == JFileChooser.APPROVE_OPTION)
				{
					updateTable.clearSelection();
					if (passedFiles.isEmpty())
					{
						updatingFiles.removeAllElements();
						for (File element : fc.getSelectedFiles())
							updatingFiles.addElement(element.toString());
					}
					else
						updatingFiles = new Vector<>(passedFiles);

					// Clear table
					updateDataVector.removeAllElements();
					updateDescriptionVector.removeAllElements();
					descriptionTextArea.setText("");

					try
					{
						for (String element : updatingFiles)
						{
							// i.e. biuf files
							final ZipFile zipFile = new ZipFile(element);
							for (Enumeration<? extends ZipEntry> en = zipFile.entries(); en.hasMoreElements(); )
							{
								final ZipEntry zipEntry = en.nextElement();
								if (zipEntry.getName().equals("info"))
								{
									// Use BufferedReader to get one line at a time
									final BufferedReader zipReader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(zipEntry), StandardCharsets.UTF_8));

									final String shortDescription = zipReader.readLine();
									float importedDBVersion = Float.parseFloat(zipReader.readLine()); // Version 1.6

									// Check if db is compatible with this version
									if (importedDBVersion > currentSystemVersion || importedDBVersion < currentDBVersion)
									{
										updateDataVector.addElement(new UpdatesData(false, shortDescription, String.valueOf(importedDBVersion), (float) ((int) (new File(element).length() / 10486)) / 100 + " MB", true));
										updateDescriptionVector.addElement("[-] " + translations[12]);
										break;
									}

									updateDataVector.addElement(new UpdatesData(false, shortDescription, String.valueOf(importedDBVersion), (float) ((int) (new File(element).length() / 10486)) / 100 + " MB", false));

									String detailedDescription = "";
									while (zipReader.ready())
										detailedDescription = detailedDescription + zipReader.readLine() + System.lineSeparator();
									zipReader.close();

									updateDescriptionVector.addElement("[+] " + detailedDescription);
									break;
								}
							}
							zipFile.close();
						}
					}
					catch (Exception ex)
					{
						ex.printStackTrace();
					}
				}
				else
					System.out.println("Attachment cancelled by user.");
				updateTable.updateUI();
			}
		});

		pathButton = new JButton(translations[31]);
		pathButton.setToolTipText(translations[32]);
		pathButton.setEnabled(false);
		pathButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				final String path1 = exportedBooksLabel.getText();
				final JFileChooser fc = new JFileChooser();
				if (path1.equals("<APP ROOT PATH>/pdf/")) // set the current folder
					fc.setSelectedFile(new File(ArabicIndexer.programFolder + "pdf"));
				else
					fc.setSelectedFile(new File(path1));

				if (ArabicIndexer.language != ArabicIndexer.lang.English)
					fc.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
				fc.setDialogTitle(translations[32]);
				fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

				final int returnVal = fc.showOpenDialog(Update.this);
				if (returnVal == JFileChooser.APPROVE_OPTION)
				{
					final File f = fc.getSelectedFile();

					// Version 1.6
					// canWrite() is buggy for Windows. TODO: Solution is Java 7 as in:
					//http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4420020
					if (!f.canWrite() && !ArabicIndexer.isWindows)
					{
						JOptionPane.showOptionDialog(getContentPane(), translations[0], translations[5], JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[18]}, translations[18]);
						return;
					}

					// Version 1.6
					// TODO: canWrite() is not working in Windows. Work Around is followed as in:
					//http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6203387
					// Re-implement once Java 7 is available
					if (ArabicIndexer.isWindows)
					{
						final String path = f.toString();
						final File testFile = new File((path.endsWith("\\") ? path : (path + '\\')) + "_testFilENaMe108");

						try
						{
							testFile.createNewFile();
							testFile.delete();
						}
						catch (Exception ex)
						{
							ex.printStackTrace();
							JOptionPane.showOptionDialog(getContentPane(), translations[0], translations[5], JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[18]}, translations[18]);
							return;
						}
					}

					// Version 1.9
					//if (f.toString().equals(new File(ArabicIndexer.programFolder + "pdf").toString()))
					if (f.compareTo(new File(ArabicIndexer.programFolder + "pdf")) == 0)
						exportedBooksLabel.setText("<APP ROOT PATH>/pdf/");
					else
					{
						String path = f.toString();
						if (!path.endsWith(File.separator)) // i.e. Not driver C:\ OR D:\ OR ...
							path = path + File.separator;
						exportedBooksLabel.setText(path);
					}
				}
				else
					System.out.println("Attachment cancelled by user.");
			}
		});
		choicePanel.add(pathButton);

		updateButton = new JButton(translations[33]);
		updateButton.setEnabled(false);
		updateButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				updateButton.setEnabled(false);
				browserButton.setEnabled(false);
				updateTable.setEnabled(false);
				exportedBooksLabel.setEnabled(false); // Version 1.7
				exportWithBooksCheckBox.setEnabled(false);
				pathButton.setEnabled(false);
				updateLabel.setText(translations[16]);

				updateThread = new Thread()
				{
					public void run()
					{
						final Random r = new Random();
						String notExportedBooks = "";
						for (int i = 0; i < tableModel.getRowCount() && !Thread.currentThread().isInterrupted(); i++) // Version 1.6
						{
							if (((Boolean) tableModel.getValueAt(i, UpdateTableModel.INSTALL_COL)))
							{
								try
								{
									final byte[] buffer = new byte[2048];
									final ZipInputStream inStream = new ZipInputStream(Channels.newInputStream(new FileInputStream(updatingFiles.elementAt(i).startsWith("http") ? (ArabicIndexer.programFolder + "temp/downloadedFile") : updatingFiles.elementAt(i)).getChannel())); // NIO for thread interrupt
									for (ZipEntry entry = inStream.getNextEntry(); entry != null; entry = inStream.getNextEntry())
									{
										// This to avoid problems by importing biuf file created in another OS
										final String zippedFileName = ArabicIndexer.isWindows ? entry.getName().replace('/', '\\') : entry.getName().replace('\\', '/');

										// Version 1.3, To extract a folder we must create it first.
										if (zippedFileName.contains(File.separator))
										{
											final StringTokenizer tokens = new StringTokenizer(zippedFileName, File.separator);
											final File f = new File(ArabicIndexer.programFolder + "temp/" + tokens.nextToken());
											f.mkdir();
										}

										// Write the files to the disk
										int nrBytesRead;
										final OutputStream outStream = Channels.newOutputStream(new FileOutputStream(ArabicIndexer.programFolder + "temp/" + entry.getName()).getChannel()); // NIO for thread interrupt
										while ((nrBytesRead = inStream.read(buffer, 0, 2048)) > 0)
											outStream.write(buffer, 0, nrBytesRead);
										outStream.close();

										/*
										int count;
										final FileOutputStream fos = new FileOutputStream("temp/"+zippedFileName);
										final BufferedOutputStream dest = new BufferedOutputStream(fos, 2048);
										while((count = zis.read(buffer, 0, 2048)) != -1)
										   dest.write(buffer, 0, count);

										dest.close();
										fos.close();
										*/
									}
									inStream.close();

									if (new File(ArabicIndexer.programFolder + "temp/" + ArabicIndexer.bookTableName).exists())
									{
										final Statement stmt = AI.sharedDBConnection.createStatement();
										final BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(ArabicIndexer.programFolder + "temp/" + ArabicIndexer.bookTableName), StandardCharsets.UTF_8));

										final int pdf_pref = r.nextInt() & Integer.MAX_VALUE; // only positive numbers

										while (in.ready())
										{
											final StringTokenizer tokens = new StringTokenizer(in.readLine(), "ö");
											final String nameToken = tokens.nextToken();
											final String parentToken = (tokens.countTokens() == 5) ? tokens.nextToken() : "";// note that we count from this token to the end not from the beginning (i.e. 5 not 6)
											final String categoryToken = tokens.nextToken();
											final String authorToken = tokens.nextToken();
											final String pdfNameToken = tokens.nextToken();
											final String txtNameToken = tokens.nextToken();
											final String pathToken;

											if (exportedBooksLabel.getText().equals("<APP ROOT PATH>/pdf/")) // Version 1.9
											{
												if (new File(ArabicIndexer.programFolder + "pdf/" + pdfNameToken).exists())
												{
													final Path pdfTemp = Paths.get(ArabicIndexer.programFolder + "temp/" + pdfNameToken);
													if (Files.exists(pdfTemp))
													{
														// Version 2.1, Allow having the same pdf file for multiple books
														if (Files.mismatch(pdfTemp, Paths.get(ArabicIndexer.programFolder + "pdf/" + pdfNameToken)) == -1)
															pathToken = "root:pdf" + File.separator + pdfNameToken;
														else
															pathToken = "root:pdf" + File.separator + (pdf_pref + "_") + pdfNameToken;
													}
													else
														// i.e. the file is matched before and moved to pdf folder
														pathToken = "root:pdf" + File.separator + pdfNameToken;
												}
												else
													pathToken = "root:pdf" + File.separator + pdfNameToken;
											}
											else
												pathToken = exportedBooksLabel.getText() + pdfNameToken;

											try
											{
												int id = 0;
												final PreparedStatement ps1 = AI.sharedDBConnection.prepareStatement("INSERT INTO " + ArabicIndexer.bookTableName + " VALUES (default, '" + nameToken + "', '" + parentToken + "', '" + categoryToken + "', '" + authorToken + "', '" + pathToken + "')", Statement.RETURN_GENERATED_KEYS);
												ps1.executeUpdate();
												try (ResultSet rs = ps1.getGeneratedKeys();)
												{
													if (rs.next())
														id = rs.getInt(1);
												}

												// Version 1.6
												stmt.execute("CREATE TABLE b" + id + "(page INTEGER, content CLOB(400000))"); // Version 1.7, CLOB instead of VARCHAR or LONGVARCHAR (H2) or LONG VARCHAR (Derby).
												stmt.execute("CREATE UNIQUE INDEX i" + id + "Page ON b" + id + "(page)");

												int pageNo = 1;
												String pageContent = "";
												boolean isLastPageEmpty = false; // Version 1.7, to check if the last page empty or not since the last page should be included in all cases.
												final BufferedReader buf = new BufferedReader(new InputStreamReader(new FileInputStream(ArabicIndexer.programFolder + "temp/" + txtNameToken), StandardCharsets.UTF_8));
												while (buf.ready())
												{
													final String str = buf.readLine();
													if (str.contains("ööööö"))
													{
														if (!pageContent.isEmpty())
														{
															final PreparedStatement ps = AI.sharedDBConnection.prepareStatement("INSERT INTO b" + id + " VALUES (" + pageNo + ", ?)");
															//ps.setString(1, pageContent);
															ps.setCharacterStream(1, new StringReader(pageContent));
															ps.execute();
															pageContent = "";
															isLastPageEmpty = false;
														}
														else
															isLastPageEmpty = true;
														pageNo++;
													}
													else
													{
														if (pageContent.isEmpty()) // For the first line
															pageContent = str;
														else
															pageContent = pageContent + System.lineSeparator() + str;
													}
												}

												if (isLastPageEmpty)
													stmt.execute("INSERT INTO b" + id + " VALUES (" + (--pageNo) + ", '')");

												buf.close();

												// This will try to copy the indexing files if exist to their location (i.e. indexingFiles/).
												//new CopyFile("temp/"+txtNameToken, "indexingFiles/"+txtNameToken);

												if (exportWithBooksCheckBox.isSelected())
												{
													final Path pdfTemp = Paths.get(ArabicIndexer.programFolder + "temp/" + pdfNameToken);
													if (Files.exists(pdfTemp))
														// This will try to copy the PDF documents if exist to their location.
														//new CopyFile(ArabicIndexer.programFolder + "temp/" + absoluteFileNameToken, pathToken.replaceFirst("root:pdf", ArabicIndexer.eProgramFolder + "pdf")); // Version 1.9
														Files.move(pdfTemp, Paths.get(pathToken.replaceFirst("root:pdf", ArabicIndexer.eProgramFolder + "pdf")), StandardCopyOption.REPLACE_EXISTING); // Version 2.1, move instead of copy to speed performance
												}
											}
											catch (SQLException ex)
											{
												System.err.println("SQLException: " + ex.getMessage());
												// H2 -> "Unique index", Derby -> "unique index"
												if (ex.getMessage().contains("nique index"))
													notExportedBooks = notExportedBooks + System.lineSeparator() + '[' + categoryToken + (parentToken.length() == 0 ? "" : (", " + parentToken)) + ", " + nameToken + "][" + pathToken + ']';
											}
										}

										stmt.close();
										in.close();
									}
									else
										JOptionPane.showOptionDialog(getContentPane(), translations[46], translations[5], JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{translations[18]}, translations[18]);
								}
								catch (Throwable t) // To catch OutOfMemoryError
								{
									System.out.println((String) tableModel.getValueAt(i, UpdateTableModel.DESCRIPTION_COL));
									t.printStackTrace();
									if (t.toString().contains("java.lang.OutOfMemoryError"))
									{
										System.gc();
										JOptionPane.showOptionDialog(getContentPane(), translations[40] + System.lineSeparator() + translations[41] + System.lineSeparator() + translations[42] + System.lineSeparator() + translations[43], translations[5], JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[18]}, translations[18]);
									}
								}
							}
						}

						// Version 1.6
						if (notExportedBooks.length() != 0)
							JOptionPane.showOptionDialog(getContentPane(), ArabicIndexer.getOptionPaneScrollablePanel(translations[45], notExportedBooks.substring(System.lineSeparator().length())), translations[5], JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{translations[18]}, translations[18]);

						SwingUtilities.invokeLater(new Runnable()
						{
							public void run()
							{
								// Enable them after updating
								browserButton.setEnabled(true);
								updateTable.setEnabled(true);
								exportedBooksLabel.setEnabled(true); // Version 1.7

								descriptionTextArea.setText("");
								updateTable.clearSelection();
							}
						});

						updatingFiles.removeAllElements();
						updateDataVector.removeAllElements();
						updateDescriptionVector.removeAllElements();

						// Version 1.3, Clear the temp folder
						final File[] deletedFiles = new File(ArabicIndexer.programFolder + "temp/").listFiles(); // Version 1.5
						if(deletedFiles != null)
						{
							for (File e : deletedFiles)
							{
								// Version 1.3, Deleting folders.
								if (e.isDirectory())
								{
									final File[] folderFiles = e.listFiles();
									if(folderFiles != null)
									{
										for (File f : folderFiles)
											f.delete();
									}
								}
								e.delete();
							}
						}

						/*
						// Clear the temp folder
						// Check this for nested folders: http://www.rgagnon.com/javadetails/java-0483.html
						final File deletedFiles [] = new File("temp/").listFiles();
						for(File element : deletedFiles)
							element.delete();
						*/

						SwingUtilities.invokeLater(new Runnable()
						{
							public void run()
							{
								updateLabel.setText(translations[6]);

								// To refresh detailList
								AI.createNodes();
								AI.orderButton.doClick();
								updateTable.updateUI(); // Version 1.6
							}
						});
					}
				};
				updateThread.setName("MyThreads: updateThread");
				updateThread.start();
			}
		});
		choicePanel.add(updateButton);

		final JButton cancelButton = new JButton(translations[34]);
		cancelButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				if (updateThread != null)
				{
					if (updateThread.isAlive())
					{
						if (JOptionPane.YES_OPTION == JOptionPane.showOptionDialog(getContentPane(), translations[3], translations[17], JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{translations[1], translations[2]}, translations[2]))
						{
							updateThread.interrupt(); // Remember that it will affect only the current blocking IO, the next IO in the thread will not be blocked, we will use 'if(isInterrupted) break' for that
							Update.this.dispose();
						}
					}
					else
						Update.this.dispose();
				}
				else
					Update.this.dispose();
			}
		});
		choicePanel.add(cancelButton);
		if (ArabicIndexer.language != ArabicIndexer.lang.English)
			getContentPane().applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter()
		{
			public void windowClosing(WindowEvent e)
			{
				cancelButton.doClick();
			}
		});
		pack();
		ArabicIndexer.centerInScreen(this);

		if (!passedFiles.isEmpty())
			browserButton.doClick();

		passedFiles.removeAllElements(); // passedFiles is a pointer to AI.updateFiles
		setVisible(true);
	}
}