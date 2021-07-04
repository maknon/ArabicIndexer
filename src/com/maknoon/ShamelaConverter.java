package com.maknoon;

import java.io.*;
import javax.swing.*;
import java.awt.event.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.sql.*;
import java.util.zip.*;
import javax.swing.border.TitledBorder;
import com.alee.laf.filechooser.WebFileChooser;

public class ShamelaConverter extends JDialog
{
	final JTextArea logging;

	public ShamelaConverter(final ArabicIndexer MAI)
	{
		super(MAI, true);

		final String[] translation = ArabicIndexer.StreamConverter(ArabicIndexer.programFolder + "language/ShamelaConvertor.txt");
		setTitle(translation[0]);

		final JPanel controlPanel = new JPanel(new FlowLayout());
		controlPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), translation[1], TitledBorder.CENTER, 0, null, Color.red));
		add(controlPanel, BorderLayout.SOUTH);

		final WebFileChooser fc = new WebFileChooser();
		fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
		//fc.setGenerateThumbnails(false);
		fc.getFileChooserPanel().setGenerateThumbnails(false);
		fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(translation[2], "bok"));
		fc.setAcceptAllFileFilterUsed(false);
		fc.setMultiSelectionEnabled(true);

		logging = new JTextArea();
		logging.setEditable(false);
		logging.setBackground(Color.WHITE);
		logging.setLineWrap(true);
		logging.setWrapStyleWord(true);
		add(new JScrollPane(logging), BorderLayout.CENTER);

		// Version 1.6, To prevent out of memory exceptions, we need to limit the rows
		logging.getDocument().addDocumentListener(new LimitLinesDocumentListener(500));

		try
		{
			//Class.forName("sun.jdbc.odbc.JdbcOdbcDriver"); // Version 1.8, Not working in Java 8, ucanaccess is a replacement
			final BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(ArabicIndexer.programFolder + "language/ShamelaConvertorNotes.txt"), StandardCharsets.UTF_8));
			while (in.ready()) append(in.readLine() + System.lineSeparator());
			in.close();
		}
		catch (Exception e)
		{
			append(e.getMessage() + System.lineSeparator() + System.lineSeparator());
			e.printStackTrace();
		}

		final JButton browse = new JButton(translation[3], new ImageIcon(ArabicIndexer.programFolder + "images/open.png"));
		browse.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				final int returnVal = fc.showOpenDialog(ShamelaConverter.this);
				if (returnVal == JFileChooser.APPROVE_OPTION)
				{
					final Thread thread = new Thread()
					{
						public void run()
						{
							final long startTime = System.nanoTime();

							final File[] files = fc.getSelectedFiles();
							for (final File f : files)
							{
								try
								{
									//final Connection con = DriverManager.getConnection("jdbc:odbc:Driver={Microsoft Access Driver (*.mdb)};DBQ="+f+";DriverID=22;READONLY=true}", "", "");
									final Connection con = DriverManager.getConnection("jdbc:ucanaccess://" + f + ";memory=false;singleconnection=true");

									// Version 1.8, Part Column can be int or string
									boolean isPartColumnInt = true;
									final DatabaseMetaData meta = con.getMetaData();
									final ResultSet rsColumns = meta.getColumns(null, null, "%", null);
									while (rsColumns.next())
									{
										final String tableName = rsColumns.getString("TABLE_NAME").toLowerCase(); // Version 1.9, toLowerCase()
										final String columnName = rsColumns.getString("COLUMN_NAME").toLowerCase();

										if (tableName.startsWith("b") && columnName.equals("part"))
										{
											if (rsColumns.getString("TYPE_NAME").equals("VARCHAR"))
												isPartColumnInt = false;
											break;
										}
									}
									rsColumns.close();

									final Statement stmt1 = con.createStatement();
									final Statement stmt2 = con.createStatement();
									final Statement stmt3 = con.createStatement();
									final Statement stmt4 = con.createStatement();
									final Statement stmt5 = con.createStatement();

						    		/* FOR TEST ONLY
						    		final ResultSet rs10 = stmt1.executeQuery("SELECT COUNT(sFileName) AS size FROM sPdf");
						    		if(rs10.next())
						    		{
						    			int i = rs10.getInt("size");
						    			if(i>0)
						    				append(files[j]+": "+i+" "+System.lineSeparator());
						    		}
						    		con.close();

						    		if(true)
						    			continue;
						    		*/

									int bookNumber = 1;
									final ResultSet rs1 = stmt1.executeQuery("SELECT * FROM Main");
									while (rs1.next())
									{
										// Note any .getXX will clear the result for the next time. It will not catch them i.e. the second .getXX will return 'null'
										// Version 1.8, getString("...") instead of getBytes("...") since we are using now ucanaccess
										final String bookName = rs1.getString("Bk");
										append(translation[6] + bookName + System.lineSeparator());
										System.out.println(translation[6] + bookName + System.lineSeparator());

										final String bookId = rs1.getString("BkId");
										String author = rs1.getString("Auth"); // Version 1.6
										if (author == null || author.isEmpty())
											author = "غير محدد";
										final String table = 'b' + bookId;

										String category = rs1.getString("cat");
										String oNum = rs1.getString("oNum");
										if (oNum == null || oNum.isEmpty())
										{
											System.out.println("oNum is null/empty, bookId = " + bookId + " bookName: " + bookName + System.lineSeparator());
											System.out.println(System.lineSeparator() + "*****************************" + System.lineSeparator());
											continue;
										}

										if (category == null || category.isEmpty())
										{
											System.out.println("category is null/empty for bookId = " + bookId + " oNum: " + oNum + " bookName: " + bookName + System.lineSeparator());
											category = translation[7];
										}

										// Create a buffer for reading the files
										final byte[] buf = new byte[1024];

										// Create the ZIP file
										final ZipOutputStream out = new ZipOutputStream(new FileOutputStream(f.getPath().substring(0, f.getPath().length() - 4) + '_' + (bookNumber++) + ".biuf"));

										final OutputStreamWriter out1 = new OutputStreamWriter(new FileOutputStream(ArabicIndexer.programFolder + "temp/info"), StandardCharsets.UTF_8);
										out1.write(bookName + System.lineSeparator());
										out1.write(ArabicIndexer.biuf_version + System.lineSeparator());
										//out1.write(new String(rs1.getString("Betaka").getBytes(), "cp1256")); // can be done like: new String(rs.getBytes("Betaka"), "cp1256") since it is the only one that works with Quran DB !
										out1.write(rs1.getString("Betaka"));
										out1.close();

										// Adding 'info' to the zip file.
										out.putNextEntry(new ZipEntry("info"));
										FileInputStream in = new FileInputStream(ArabicIndexer.programFolder + "temp/info");
										int len;
										while ((len = in.read(buf)) > 0)
											out.write(buf, 0, len);
										out.closeEntry();
										in.close();

										final OutputStreamWriter out2 = new OutputStreamWriter(new FileOutputStream(ArabicIndexer.programFolder + "temp/arabicBook"), StandardCharsets.UTF_8);

										int max = 0;
										// All of these are not correct:
										//SELECT max(CInt(part)) FROM table where ISNUMERIC(part)=1;
										//SELECT max(CInt(part)) FROM table where part <> null and part LIKE '[0-9]';
										// Version 1.6, add 'page <> NULL' to prevent parts without pages !
										// Version 1.8, IS NOT NULL instead of <> NULL since it is not working with ucanaccess
										// Version 1.8, replace LEN(part) > 0  since part might be int or string. use isPartColumnInt
										final ResultSet rs5;
										if (isPartColumnInt)
											rs5 = stmt5.executeQuery("SELECT COUNT(part) AS size FROM (SELECT part FROM " + table + " WHERE part IS NOT NULL AND part>0 AND page IS NOT NULL GROUP BY part)");
										else
											rs5 = stmt5.executeQuery("SELECT COUNT(part) AS size FROM (SELECT part FROM " + table + " WHERE part IS NOT NULL AND LEN(part)>0 AND page IS NOT NULL GROUP BY part)");

										if (rs5.next())
											max = rs5.getInt("size");
										rs5.close();

										if (max == 1)
										{
											// Single-volume book
											final ResultSet rs2;

											if (isPartColumnInt)
												rs2 = stmt2.executeQuery("SELECT part, MAX(page) AS size FROM " + table + " WHERE part IS NOT NULL AND part>0 AND page IS NOT NULL GROUP BY part"); // Version 1.6, add 'page <> NULL' to prevent parts without pages !
											else
												rs2 = stmt2.executeQuery("SELECT part, MAX(page) AS size FROM " + table + " WHERE part IS NOT NULL AND LEN(part)>0 AND page IS NOT NULL GROUP BY part"); // Version 1.6, add 'page <> NULL' to prevent parts without pages !
											if (rs2.next())
											{
												final int size = rs2.getInt("size");
												final String part = rs2.getString("part");
												final Vector<String> pages = new Vector<>();
												pages.setSize(size);

												//final ResultSet rs4 = stmt4.executeQuery("SELECT nass, page FROM "+table+" WHERE part <> null AND page>=1 ORDER BY page");
												// Version 1.6, order by ...,id to allow including multiple rows for the same page in order.
												final ResultSet rs4 = stmt4.executeQuery("SELECT nass, page FROM " + table + " WHERE part LIKE '" + part + "' AND page>=1 ORDER BY page,id"); // 'page>=1' to prevent empty pages.
												while (rs4.next())
												{
													final int pageNumber = rs4.getInt("page") - 1;
													final String page = pages.get(pageNumber);
													if (page == null)
														pages.set(pageNumber, rs4.getString("nass"));
													else
														pages.set(pageNumber, page + System.lineSeparator() + rs4.getString("nass")); // Version 1.6
												}
												rs4.close();

												final ResultSet rs3 = stmt3.executeQuery("SELECT sFileName FROM sPdf WHERE Part LIKE '" + part + "' AND oNum LIKE '" + oNum + "'");
												if (rs3.next())
												{
													final String pdf = rs3.getString("sFileName");
													final String txtFile = bookId + ".txt";

													out2.write(bookName + "öö" + category + 'ö' + author + 'ö' + pdf + 'ö' + txtFile + System.lineSeparator());
													final OutputStreamWriter out3 = new OutputStreamWriter(new FileOutputStream(ArabicIndexer.programFolder + "temp/" + txtFile), StandardCharsets.UTF_8);
													for (int i = 0; i < pages.size(); i++)
													{
														if (pages.elementAt(i) != null)
															out3.write(pages.elementAt(i) + System.lineSeparator());
														out3.write("öööööö " + (i + 1) + " öööööö" + System.lineSeparator());
													}
													out3.close();

													out.putNextEntry(new ZipEntry(txtFile));
													in = new FileInputStream(ArabicIndexer.programFolder + "temp/" + txtFile);
													while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
													out.closeEntry();
													in.close();

													append(translation[5] + pdf + System.lineSeparator());
												}
												else
												{
													final String txtFile = bookId + ".txt";
													out2.write(bookName + "öö" + category + 'ö' + author + 'ö' + bookId + ".pdf" + 'ö' + txtFile + System.lineSeparator());
													final OutputStreamWriter out3 = new OutputStreamWriter(new FileOutputStream(ArabicIndexer.programFolder + "temp/" + txtFile), StandardCharsets.UTF_8);
													for (int i = 0; i < pages.size(); i++)
													{
														if (pages.elementAt(i) != null)
															out3.write(pages.elementAt(i) + System.lineSeparator());
														out3.write("öööööö " + (i + 1) + " öööööö" + System.lineSeparator());
													}
													out3.close();

													out.putNextEntry(new ZipEntry(txtFile));
													in = new FileInputStream(ArabicIndexer.programFolder + "temp/" + txtFile);
													while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
													out.closeEntry();
													in.close();

													append(translation[5] + bookId + ".pdf" + System.lineSeparator());
												}
												rs3.close();
											}
											rs2.close();
										}
										else
										{
											// Multi-volume book
											// ORDER BY will not help here since it is included as part of GROUP BY. At the same time ordering 'part' column will result into two different behavior depending on the column type i.e. int or string
											final ResultSet rs2;

											if (isPartColumnInt)
												rs2 = stmt2.executeQuery("SELECT part, MAX(page) AS size FROM " + table + " WHERE part IS NOT NULL AND part>0 AND page IS NOT NULL GROUP BY part"); // Version 1.6, add 'page <> NULL' to prevent parts without pages !
											else
												rs2 = stmt2.executeQuery("SELECT part, MAX(page) AS size FROM " + table + " WHERE part IS NOT NULL AND LEN(part)>0 AND page IS NOT NULL GROUP BY part"); // Version 1.6, add 'page <> NULL' to prevent parts without pages !
											while (rs2.next())
											{
												final String part = rs2.getString("part");
												final int size = rs2.getInt("size");
												final Vector<String> pages = new Vector<>();
												pages.setSize(size);

												// part and NOT 'new String(part.getBytes(), "cp1256")' inside MS ACCESS.
												// Version 1.6, order by ...,id to allow including multiple rows for the same page in order.
												final ResultSet rs4 = stmt4.executeQuery("SELECT nass, page FROM " + table + " WHERE part LIKE '" + part + "' AND page>=1 ORDER BY page,id"); // 'page>=1' to prevent empty pages.
												while (rs4.next())
												{
													final int pageNumber = rs4.getInt("page") - 1;
													final String page = pages.get(pageNumber);
													if (page == null)
														pages.set(pageNumber, rs4.getString("nass"));
													else
														pages.set(pageNumber, page + System.lineSeparator() + rs4.getString("nass")); // Version 1.6
												}
												rs4.close();

												final ResultSet rs3 = stmt3.executeQuery("SELECT sFileName FROM sPdf WHERE Part LIKE '" + part + "' AND oNum LIKE '" + oNum + "'");
												if (rs3.next())
												{
													final String pdf = rs3.getString("sFileName");

													/* Version 2.1, pdf file should not be changed since it might be shared between multiple parts and we should not repeat the pdf files since it will increase the size. txt files will be named by bookid_part all the time
													// Version 1.8, this condition to avoid the error of having two txt files with the same name. this happens when having multi-volumn books with the same sfilename for different parts.
													if (new File(ArabicIndexer.programFolder + "temp/" + pdf + ".txt").exists())
													{
														final String prefix = part.replace('/', '-') + "_";
														out2.write(translation[4] + part + 'ö' + bookName + 'ö' + category + 'ö' + author + 'ö' + prefix + pdf + 'ö' + txtFile + System.lineSeparator());
														final OutputStreamWriter out3 = new OutputStreamWriter(new FileOutputStream(ArabicIndexer.programFolder + "temp/" + prefix + pdf + ".txt"), StandardCharsets.UTF_8);
														for (int i = 0; i < pages.size(); i++)
														{
															if (pages.elementAt(i) != null)
																out3.write(pages.elementAt(i) + System.lineSeparator());
															out3.write("öööööö " + (i + 1) + " öööööö" + System.lineSeparator());
														}
														out3.close();

														out.putNextEntry(new ZipEntry(prefix + pdf + ".txt"));
														in = new FileInputStream(ArabicIndexer.programFolder + "temp/" + prefix + pdf + ".txt");
														while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
														out.closeEntry();
														in.close();

														append(translation[4] + part + "\t->\t" + translation[5] + pdf + "\t->\t" + translation[11] + prefix + pdf + System.lineSeparator());
													}
													else
													*/
													{
														final String name = bookId + '_' + part.replace('/', '-');
														final String txtFile = name + ".txt";
														out2.write(translation[4] + part + 'ö' + bookName + 'ö' + category + 'ö' + author + 'ö' + pdf + 'ö' + txtFile + System.lineSeparator());
														final OutputStreamWriter out3 = new OutputStreamWriter(new FileOutputStream(ArabicIndexer.programFolder + "temp/" + txtFile), StandardCharsets.UTF_8);
														for (int i = 0; i < pages.size(); i++)
														{
															if (pages.elementAt(i) != null)
																out3.write(pages.elementAt(i) + System.lineSeparator());
															out3.write("öööööö " + (i + 1) + " öööööö" + System.lineSeparator());
														}
														out3.close();

														out.putNextEntry(new ZipEntry(txtFile));
														in = new FileInputStream(ArabicIndexer.programFolder + "temp/" + txtFile);
														while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
														out.closeEntry();
														in.close();

														append(translation[4] + part + "\t->\t" + translation[5] + pdf + System.lineSeparator());
													}
												}
												else
												{
													final String name = bookId + '_' + part.replace('/', '-');
													final String txtFile = name + ".txt";
													final String pdfFile = name + ".pdf";

													// Version 1.8, part might have special character e.g. "/" we needs to replace it since it will be part of the file name
													out2.write(translation[4] + part + 'ö' + bookName + 'ö' + category + 'ö' + author + 'ö' + pdfFile + 'ö' + txtFile + System.lineSeparator());
													final OutputStreamWriter out3 = new OutputStreamWriter(new FileOutputStream(ArabicIndexer.programFolder + "temp/" + txtFile), StandardCharsets.UTF_8);
													for (int i = 0; i < pages.size(); i++)
													{
														if (pages.elementAt(i) != null)
															out3.write(pages.elementAt(i) + System.lineSeparator());
														out3.write("öööööö " + (i + 1) + " öööööö" + System.lineSeparator());
													}
													out3.close();

													out.putNextEntry(new ZipEntry(txtFile));
													in = new FileInputStream(ArabicIndexer.programFolder + "temp/" + txtFile);
													while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
													out.closeEntry();
													in.close();

													append(translation[4] + part + "\t->\t" + translation[5] + pdfFile + (part.contains("/") ? "\t->\t" + translation[10] + part : "") + System.lineSeparator());
												}
												rs3.close();
											}
											rs2.close();
										}

										out2.close();

										out.putNextEntry(new ZipEntry("arabicBook"));
										in = new FileInputStream(ArabicIndexer.programFolder + "temp/arabicBook");
										while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
										out.closeEntry();
										out.close(); // Complete the ZIP file
										in.close();

										if (max == 0) // Version 1.8, book does not have part i.e. mainly archaive doc e.g. أرشيف منتدى الألوكة
										{
											new File(f.getPath().substring(0, f.getPath().length() - 4) + '_' + (bookNumber - 1) + ".biuf").delete();
											append(translation[12] + System.lineSeparator());
										}
										append(System.lineSeparator() + "*****************************" + System.lineSeparator());
									}

									// Clear the temp folder
									// Version 1.8, clearing is done for every book so that we can check multi-volume books with similar pdf filename for different parts of the book. in this case the pdf.txt will be available and we can check the file
									final File[] deletedFiles = new File(ArabicIndexer.programFolder + "temp/").listFiles();
									if(deletedFiles != null)
									{
										for (File element : deletedFiles)
											element.delete();
									}

									rs1.close();
									stmt1.close();
									stmt2.close();
									stmt3.close();
									stmt4.close();
									stmt5.close();
									con.close();
								}
								catch (Exception ex)
								{
									append(ex.getMessage() + System.lineSeparator() + System.lineSeparator());
									ex.printStackTrace();
								}
							}

							browse.setEnabled(true);

							final int duration = (int) ((System.nanoTime() - startTime) / 1000000000.0);
							final String hour = String.valueOf(duration / 3600);
							final String minute = String.valueOf(duration / 60 - (duration / 3600) * 60);
							final String second = String.valueOf(duration - ((int) ((float) duration / 60) * 60));

							append(translation[8] + hour + ':' + minute + ':' + second + System.lineSeparator());
							append(translation[9] + files.length + System.lineSeparator());
						}
					};
					thread.start();
					browse.setEnabled(false);
				}
			}
		});
		controlPanel.add(browse);

		if (ArabicIndexer.language != ArabicIndexer.lang.English)
			applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

		setSize(700, 500);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setResizable(false);
		ArabicIndexer.centerInScreen(this);
		setVisible(true);
	}

	void append(final String line)
	{
		SwingUtilities.invokeLater(new Runnable()
		{
			public void run() // Version 1.8, it through some time java.lang.IllegalArgumentException: bad position ...
			{
				logging.append(line);
				logging.setCaretPosition(logging.getDocument().getLength());
			}
		});
	}
}