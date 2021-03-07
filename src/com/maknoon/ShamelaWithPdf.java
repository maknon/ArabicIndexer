package com.maknoon;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.sql.*;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.*;

public class ShamelaWithPdf
{
	final String pdfPath = "F:/shamela=PDF12300/pdf";
	final String pdfDB = "C:/Users/Ebrahim/Desktop/migration/special.accdb";
	final String bokFolder = "C:/Users/Ebrahim/Desktop/migration/bok";

	public static void main(String[] args)
	{
		try
		{
			// Not working
			//System.setProperty("hsqldb.reconfig_logging", "false");
			//Logger.getLogger("hsqldb.db").setLevel(Level.OFF);

			final Logger databaseLogger = Logger.getLogger("hsqldb.db");
			databaseLogger.setUseParentHandlers(false);
			databaseLogger.setLevel(Level.WARNING);
			databaseLogger.addHandler(new FileHandler("shamela.log"));

			new ShamelaWithPdf();
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}

	public ShamelaWithPdf() throws Exception
	{
		final String[] translation = ArabicIndexer.StreamConverter("language/ShamelaConvertor.txt");

		final Connection conPDF = DriverManager.getConnection("jdbc:ucanaccess://" + pdfDB + ";memory=false;singleconnection=true");
		final Statement stmtPDF = conPDF.createStatement();

		final Vector<String> oNum_pdf = new Vector<>(5000, 500);
		//final Vector<String> PdfPath_pdf = new Vector<>(5000, 500);
		final Vector<String> PdfPath_deleted = new Vector<>(5000, 500);
		//final Vector<String> Part_pdf = new Vector<>(5000, 500);

		final Vector<String> BkId_0pdf = new Vector<>(5000, 500);
		final Vector<String> PdfPath_0pdf_deleted = new Vector<>(5000, 500);

		ResultSet rsPDF = stmtPDF.executeQuery("SELECT * FROM oPdf");
		while (rsPDF.next())
		{
			final String pdf = rsPDF.getString("PdfPath");
			final String path = pdf.replaceFirst("Rel:pdf", pdfPath);

			if (new File(path).exists())
			{
				oNum_pdf.add(rsPDF.getString("oNum"));
				//PdfPath_pdf.add(pdf);
				//Part_pdf.add(rsPDF.getString("Part"));
			}
			else
			{
				//rsPDF.deleteRow();
				PdfPath_deleted.add(pdf);
			}
		}
		rsPDF.close();

		// Delete the rows that has non-existed pdf files
		for (final String s : PdfPath_deleted)
		{
			System.out.println(s + System.lineSeparator());
			stmtPDF.executeUpdate("DELETE FROM oPdf WHERE PdfPath = '" + s + "'");
		}

		rsPDF = stmtPDF.executeQuery("SELECT * FROM 0pdf");
		while (rsPDF.next())
		{
			final String pdf = rsPDF.getString("PdfPath");
			final String path = pdf.replaceFirst("Rel:pdf", pdfPath);

			if (new File(path).exists())
				BkId_0pdf.add(rsPDF.getString("BkId"));
			else
				PdfPath_0pdf_deleted.add(pdf);
		}
		rsPDF.close();

		System.out.println("0pdf" + System.lineSeparator());

		// Delete the rows that has non-existed pdf files in 0pdf
		for (final String s : PdfPath_0pdf_deleted)
		{
			System.out.println(s + System.lineSeparator());
			stmtPDF.executeUpdate("DELETE FROM 0pdf WHERE PdfPath = '" + s + "'");
		}

		final long startTime = System.nanoTime();

		final Thread[] threads = new Thread[8];
		for (int k = 0; k < threads.length; k++)
		{
			final int q = k;
			threads[q] = new Thread()
			{
				public void run()
				{
					try
					{
						final OutputStreamWriter log = new OutputStreamWriter(new FileOutputStream("log" + q + ".txt"), StandardCharsets.UTF_8);
						final File[] files = new File(bokFolder + q).listFiles();
						if(files != null)
						{
							for (final File f : files)
							{
								final Connection con = DriverManager.getConnection("jdbc:ucanaccess://" + f + ";memory=false;singleconnection=true");

								// Version 1.8, Part Column can be int or string
								boolean isPartColumnInt = true;
								boolean withPDF = false;
								final DatabaseMetaData meta = con.getMetaData();
								final ResultSet rsColumns = meta.getColumns(null, null, "%", null);
								while (rsColumns.next())
								{
									final String tableName = rsColumns.getString("TABLE_NAME").toLowerCase();
									final String columnName = rsColumns.getString("COLUMN_NAME").toLowerCase();

									if (tableName.startsWith("b") && columnName.equals("part"))
									{
										if (rsColumns.getString("TYPE_NAME").equals("VARCHAR"))
											isPartColumnInt = false;
										//break; // Version 2.1
									}

									//  Version 2.1, oNum in Main table might be different than bkId which is part of tableName "b+bkId"
									/*
									if (tableName.startsWith("b"))
									{
										if (oNum_pdf.contains(tableName.substring(1)))
											withPDF = true;
										//else // Version 2.1
											//break;
									}
									*/
								}
								rsColumns.close();

								// Version 2.1
								/*
								if (!withPDF)
								{
									con.close();
									System.out.println("No file associated with: " + f);
									continue;
								}
								*/

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
										append(files[j]+": "+i+" "+lineSeparator);
								}
								con.close();

								if(true)
									continue;
								*/

								final Path tempDir = Files.createTempDirectory("shamela_");

								int bookNumber = 1;
								final ResultSet rs1 = stmt1.executeQuery("SELECT * FROM Main");
								while (rs1.next())
								{
									boolean withError = false;
									boolean check_0pdf = false;

									// Note any .getXX will clear the result for the next time. It will not catch them i.e. the second .getXX will return 'null'
									// Version 1.8, getString("...") instead of getBytes("...") since we are using now ucanaccess
									final String bookName = rs1.getString("Bk");
									final String bookId = rs1.getString("BkId");
									String author = rs1.getString("Auth"); // Version 1.6
									if (author == null || author.isEmpty())
										author = "غير محدد";
									final String table = 'b' + bookId;

									String category = rs1.getString("cat");
									final String oNum = rs1.getString("oNum");

									log.write(translation[6] + bookName + System.lineSeparator());
									log.write("oNum = " + oNum + " BookId = " + bookId + System.lineSeparator());
									log.write(f + System.lineSeparator());

									if (oNum == null || oNum.isEmpty())
									{
										check_0pdf = true;
										if (!BkId_0pdf.contains(bookId))
										{
											log.write("Not associated with any PDF file in 0pdf (oNum is null/empty), BookId = " + bookId + " bookName: " + bookName + System.lineSeparator());
											log.write(System.lineSeparator() + "*****************************" + System.lineSeparator());
											continue;
										}
										else
											log.write("0pdf is used, BookId = " + bookId + System.lineSeparator());
									}
									else
									{
										if (!oNum_pdf.contains(oNum))
										{
											log.write("Not associated with any PDF file in oPdf, oNum = " + oNum + " bookId = " + bookId + " bookName: " + bookName + System.lineSeparator());
											log.write(System.lineSeparator() + "*****************************" + System.lineSeparator());
											continue;
										}
									}

									if (category == null || category.isEmpty())
									{
										log.write("category is null/empty for bookId = " + bookId + " oNum: " + oNum + " bookName: " + bookName + System.lineSeparator());
										category = translation[7];
									}

									// Create a buffer for reading the files.
									final byte[] buf = new byte[1024];

									// Create the ZIP file
									final ZipOutputStream out = new ZipOutputStream(new FileOutputStream(f.getPath().substring(0, f.getPath().length() - 4) + '_' + (bookNumber++) + ".biuf"));

									final OutputStreamWriter out1 = new OutputStreamWriter(new FileOutputStream(new File(tempDir.toFile(), "info")), StandardCharsets.UTF_8);
									out1.write(bookName + System.lineSeparator());
									out1.write(ArabicIndexer.StreamConverter("setting/version.txt")[1] + System.lineSeparator());
									//out1.write(new String(rs1.getString("Betaka").getBytes(), "cp1256")); // can be done like: new String(rs.getBytes("Betaka"), "cp1256") since it is the only one that works with Quran DB !
									out1.write(rs1.getString("Betaka"));
									out1.close();

									// Adding 'info' to the zip file.
									out.putNextEntry(new ZipEntry("info"));
									FileInputStream in = new FileInputStream(new File(tempDir.toFile(), "info"));
									int len;
									while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
									out.closeEntry();
									in.close();

									final OutputStreamWriter out2 = new OutputStreamWriter(new FileOutputStream(new File(tempDir.toFile(), "arabicBook")), StandardCharsets.UTF_8);

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

									final HashSet<String> zipFilesList = new HashSet<>();
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
											final ResultSet rs4 = stmt4.executeQuery("SELECT nass, page FROM " + table + " WHERE part = '" + part + "' AND page>=1 ORDER BY page,id"); // 'page>=1' to prevent empty pages.
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

											final ResultSet rs3 = stmtPDF.executeQuery(check_0pdf
													? ("SELECT PdfPath FROM 0pdf WHERE Part = '" + part + "' AND BkId = '" + bookId + "'")
													: ("SELECT PdfPath FROM oPdf WHERE Part = '" + part + "' AND oNum = '" + oNum + "'")); // Part is important to avoid book cover. but at the same time some books does not put the Part correctly hence, it is ignored and logged for manual check
											if (rs3.next()) // There should be one
											{
												final File pdf = new File(rs3.getString("PdfPath").replaceFirst("Rel:pdf", pdfPath));
												final String txtFile = bookId + ".txt";

												out2.write(bookName + "öö" + category + 'ö' + author + 'ö' + pdf.getName() + 'ö' + txtFile + System.lineSeparator()); // Version 2.1, pdf.getName() instead of (bookId + ".pdf") to keep the same file name since all parts might have the same pdf file. there are many cases the biuf file is 5GB and the pdf file is repeated 19 times.
												final OutputStreamWriter out3 = new OutputStreamWriter(new FileOutputStream(new File(tempDir.toFile(), txtFile)), StandardCharsets.UTF_8);
												for (int i = 0; i < pages.size(); i++)
												{
													if (pages.elementAt(i) != null)
														out3.write(pages.elementAt(i) + System.lineSeparator());
													out3.write("öööööö " + (i + 1) + " öööööö" + System.lineSeparator());
												}
												out3.close();

												out.putNextEntry(new ZipEntry(txtFile));
												in = new FileInputStream(new File(tempDir.toFile(), txtFile));
												while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
												out.closeEntry();
												in.close();

												out.putNextEntry(new ZipEntry(pdf.getName())); // Version 2.1
												in = new FileInputStream(pdf);
												while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
												out.closeEntry();
												in.close();

												log.write(translation[5] + pdf.getName() + System.lineSeparator()); // Version 2.1
											}
											else
											{
												log.write("bookId = " + bookId + " part: " + part + " oNum: " + oNum + " " + translation[6] + bookName + "        رقم الجزء غير متطابق" + System.lineSeparator());
												withError = true;
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
											final ResultSet rs4 = stmt4.executeQuery("SELECT nass, page FROM " + table + " WHERE part = '" + part + "' AND page>=1 ORDER BY page,id"); // 'page>=1' to prevent empty pages.
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

											final ResultSet rs3 = stmtPDF.executeQuery(check_0pdf
													? ("SELECT PdfPath FROM 0pdf WHERE Part = '" + part + "' AND BkId = '" + bookId + "'")
													: ("SELECT PdfPath FROM oPdf WHERE Part = '" + part + "' AND oNum = '" + oNum + "'"));
											if (rs3.next()) // There should be one
											{
												final File pdf = new File(rs3.getString("PdfPath").replaceFirst("Rel:pdf", pdfPath));
												final String name = bookId + '_' + part.replace('/', '-');
												final String txtFile = name + ".txt";

												// Version 1.8, part might have special character e.g. "/" we needs to replace it since it will be part of the file name
												out2.write(translation[4] + part + 'ö' + bookName + 'ö' + category + 'ö' + author + 'ö' + pdf.getName() + 'ö' + txtFile + System.lineSeparator()); // Version 2.1, pdf.getName() instead of 'name + ".pdf"'
												final OutputStreamWriter out3 = new OutputStreamWriter(new FileOutputStream(new File(tempDir.toFile(), txtFile)), StandardCharsets.UTF_8);
												for (int i = 0; i < pages.size(); i++)
												{
													if (pages.elementAt(i) != null)
														out3.write(pages.elementAt(i) + System.lineSeparator());
													out3.write("öööööö " + (i + 1) + " öööööö" + System.lineSeparator());
												}
												out3.close();

												out.putNextEntry(new ZipEntry(txtFile));
												in = new FileInputStream(new File(tempDir.toFile(), txtFile));
												while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
												out.closeEntry();
												in.close();

												if(zipFilesList.add(pdf.getName())) // To avoid adding the same pdf file again, duplicate entries will throw exception
												{
													out.putNextEntry(new ZipEntry(pdf.getName()));
													in = new FileInputStream(pdf);
													while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
													out.closeEntry();
													in.close();
												}

												log.write(translation[4] + part + "\t->\t" + translation[5] + name + ".pdf" + (part.contains("/") ? "\t->\t" + translation[10] + part : "") + System.lineSeparator());
											}
											else
											{
												log.write("bookId = " + bookId + " part: " + part + " oNum: " + oNum + " " + translation[6] + bookName + " أحد أجزاء الكتاب (PDF) مفقود أو رقم الجزء غير متطابق" + System.lineSeparator());
												withError = true;
											}
											rs3.close();
										}
										rs2.close();
									}

									out2.close();

									out.putNextEntry(new ZipEntry("arabicBook"));
									in = new FileInputStream(new File(tempDir.toFile(), "arabicBook"));
									while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
									out.closeEntry();
									out.close(); // Complete the ZIP file
									in.close();

									if (max == 0) // Version 1.8, book does not have part i.e. mainly archive doc e.g. أرشيف منتدى الألوكة
									{
										new File(f.getPath().substring(0, f.getPath().length() - 4) + '_' + (bookNumber - 1) + ".biuf").delete();
										log.write(translation[12] + System.lineSeparator());
									}

									if (withError)
									{
										new File(f.getPath().substring(0, f.getPath().length() - 4) + '_' + (bookNumber - 1) + ".biuf").delete();
										log.write("تم حذف هذا الكتاب" + System.lineSeparator());
									}

									log.write(System.lineSeparator() + "*****************************" + System.lineSeparator());
									log.flush();
								}

								// Clear the temp folder
								// Version 1.8, clearing is done for every book so that we can check multi-volumn books with similar pdf filename for different parts of the book. in this case the pdf.txt will be available and we can check the file
								//final File[] deletedFiles = new File("temp/").listFiles();
								//for (File element : deletedFiles)
								//element.delete();

								tempDir.toFile().delete();

								rs1.close();
								stmt1.close();
								stmt2.close();
								stmt3.close();
								stmt4.close();
								stmt5.close();
								con.close();
							}

							final int duration = (int) ((System.nanoTime() - startTime) / 1000000000.0);
							final String hour = String.valueOf(duration / 3600);
							final String minute = String.valueOf(duration / 60 - (duration / 3600) * 60);
							final String second = String.valueOf(duration - ((int) ((float) duration / 60) * 60));

							log.write(translation[8] + hour + ':' + minute + ':' + second + System.lineSeparator());
							log.write(translation[9] + files.length + System.lineSeparator());
						}
						log.close();
					}
					catch (Exception e)
					{
						e.printStackTrace();
					}
				}
			};
			threads[q].start();
		}

		for (Thread thread : threads)
			thread.join();

		stmtPDF.close();
		conPDF.close();
	}
}