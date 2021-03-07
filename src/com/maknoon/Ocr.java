package com.maknoon;

import com.alee.laf.filechooser.WebFileChooser;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;
import org.ghost4j.Ghostscript;
import org.ghost4j.GhostscriptException;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Properties;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static net.sourceforge.tess4j.util.PdfUtilities.PDFBOX;
import static net.sourceforge.tess4j.util.PdfUtilities.PDF_LIBRARY;

public final class Ocr extends JDialog
{
	final JTextArea logging;

	static int dpi = 600;
	static boolean pdfBox = true; // gs or pdfbox
	static String traineddata = "Fast"; // Fast / Best

	public Ocr(final ArabicIndexer MAI)
	{
		super(MAI, true);

		final String[] translation = ArabicIndexer.StreamConverter(ArabicIndexer.programFolder + "language/Ocr.txt");
		setTitle(translation[0]);

		final JPanel controlPanel = new JPanel(new FlowLayout());
		controlPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), translation[1], TitledBorder.CENTER, 0, null, Color.red));
		add(controlPanel, BorderLayout.SOUTH);

		final WebFileChooser fc = new WebFileChooser();
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fc.setMultiSelectionEnabled(true);

		logging = new JTextArea();
		logging.setEditable(false);
		logging.setBackground(Color.WHITE);
		logging.setLineWrap(true);
		logging.setWrapStyleWord(true);
		add(new JScrollPane(logging), BorderLayout.CENTER);

		// To prevent out of memory exceptions, we need to limit the rows
		logging.getDocument().addDocumentListener(new LimitLinesDocumentListener(500));

		final Properties prop = new Properties();

		try
		{
			prop.load(new FileInputStream(ArabicIndexer.programFolder + "setting/setting.properties"));

			dpi = Integer.parseInt(prop.getProperty("ocr_dpi"));
			pdfBox = prop.getProperty("ocr_pdf2png").equals("pdfbox");
			traineddata = prop.getProperty("ocr_traineddata");

			//Class.forName("sun.jdbc.odbc.JdbcOdbcDriver"); // Version 1.8, Not working in Java 8, ucanaccess is a replacement
			final BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(ArabicIndexer.programFolder + "language/OcrNotes.txt"), StandardCharsets.UTF_8));
			while (in.ready()) append(in.readLine() + System.lineSeparator());
			in.close();
		}
		catch (Exception e)
		{
			append(e.getMessage() + System.lineSeparator() + System.lineSeparator());
			e.printStackTrace();
		}

		if(pdfBox)
			System.setProperty(PDF_LIBRARY, PDFBOX);
		else
			System.setProperty("jna.library.path", ArabicIndexer.programFolder + "bin");

		final JButton browse = new JButton(translation[2], new ImageIcon(ArabicIndexer.programFolder + "images/open.png"));
		browse.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				final int returnVal = fc.showOpenDialog(Ocr.this);
				if (returnVal == JFileChooser.APPROVE_OPTION)
				{
					final Thread thread = new Thread()
					{
						public void run()
						{
							final long startTime = System.nanoTime();

							final Tesseract tesseract = new Tesseract();  // JNA Interface Mapping
							tesseract.setDatapath(ArabicIndexer.programFolder + "bin");
							switch (ArabicIndexer.language)
							{
								case Arabic:
									tesseract.setLanguage("ara" + traineddata);
									break;
								case Urdu:
									tesseract.setLanguage("urd" + traineddata);
									break;
								case English:
									tesseract.setLanguage("eng" + traineddata);
									break;
							}
							tesseract.setTessVariable("user_defined_dpi", String.valueOf(dpi));
							tesseract.setPageSegMode(1);
							tesseract.setOcrEngineMode(1);

							final File dir = fc.getSelectedFile();
							final File[] listOfAuthors = dir.listFiles();
							if(listOfAuthors != null)
							{
								for (final File a : listOfAuthors)
								{
									if (a.isDirectory()) // author folder
									{
										final String author = a.getName();
										append(translation[4] + author + System.lineSeparator());

										final File[] listOfBooks = a.listFiles();
										if (listOfBooks != null)
										{
											for (final File b : listOfBooks)
											{
												if (b.isFile()) // Single-volume book
												{
													final String pdf = b.getName();

													if(pdf.endsWith(".pdf"))
													{
														final String[] tokens = pdf.split("\\+|\\.");
														final String bookName = tokens[0];
														final String category = tokens[1];

														append(translation[5] + bookName + System.lineSeparator());

														// Create a buffer for reading the files
														final byte[] buf = new byte[1024];

														try
														{
															final String biuf = b.getPath().substring(0, b.getPath().length() - 4) + ".biuf";

															// Create the ZIP file
															final ZipOutputStream out = new ZipOutputStream(new FileOutputStream(biuf));
															//out.setMethod(ZipOutputStream.DEFLATED);
															out.setLevel(Deflater.NO_COMPRESSION);

															final OutputStreamWriter out1 = new OutputStreamWriter(new FileOutputStream(ArabicIndexer.programFolder + "temp/info"), StandardCharsets.UTF_8);
															out1.write(bookName + System.lineSeparator());
															out1.write(ArabicIndexer.StreamConverter(ArabicIndexer.programFolder + "setting/version.txt")[1] + System.lineSeparator());
															out1.write(translation[4] + author + System.lineSeparator() + translation[6] + category); // book betakah, for now it is author name + category
															out1.close();

															// Adding 'info' to the zip file
															out.putNextEntry(new ZipEntry("info"));
															FileInputStream in = new FileInputStream(ArabicIndexer.programFolder + "temp/info");
															int len;
															while ((len = in.read(buf)) > 0)
																out.write(buf, 0, len);
															out.closeEntry();
															in.close();

															final OutputStreamWriter out2 = new OutputStreamWriter(new FileOutputStream(ArabicIndexer.programFolder + "temp/arabicBook"), StandardCharsets.UTF_8);
															out2.write(bookName + "öö" + category + 'ö' + author + 'ö' + pdf + System.lineSeparator());
															out2.close();

															out.putNextEntry(new ZipEntry("arabicBook"));
															in = new FileInputStream(ArabicIndexer.programFolder + "temp/arabicBook");
															while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
															out.closeEntry();
															in.close();

															final OutputStreamWriter out3 = new OutputStreamWriter(new FileOutputStream(ArabicIndexer.programFolder + "temp/" + pdf + ".txt"), StandardCharsets.UTF_8);

															//final File[] pages = PdfUtilities.convertPdf2Png(b); // Default dpi = 300
															final File[] pages = pdfBox?convertPdf2Png(b):Gs_convertPdf2Png(b); // to increase dpi

															append(translation[9]);

															for (int i = 0; i < pages.length; i++)
															{
																try
																{
																	final String result = tesseract.doOCR(pages[i]);
																	out3.write(result + System.lineSeparator());
																	out3.write("öööööö " + (i + 1) + " öööööö" + System.lineSeparator());
																	pages[i].delete();
																}
																catch (TesseractException e)
																{
																	append(e.getMessage() + System.lineSeparator() + System.lineSeparator());
																	e.printStackTrace();
																}

																append("-" + (i+1));
															}
															out3.close();

															out.putNextEntry(new ZipEntry(pdf + ".txt"));
															in = new FileInputStream(ArabicIndexer.programFolder + "temp/" + pdf + ".txt");
															while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
															out.closeEntry();
															in.close();

															// Copy pdf file
															out.putNextEntry(new ZipEntry(pdf));
															in = new FileInputStream(b);
															while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
															out.closeEntry();
															in.close();

															out.close(); // Complete the ZIP file
														}
														catch (Exception ex)
														{
															append(ex.getMessage() + System.lineSeparator() + System.lineSeparator());
															ex.printStackTrace();
														}
														append(System.lineSeparator() + "*****************************" + System.lineSeparator());
													}
												}
												else
												{
													// Multi-volume book
													final String bookFolder = b.getName();

													final String[] tokens = bookFolder.split("\\+");
													final String bookName = tokens[0];
													final String category = tokens[1];

													append(translation[5] + bookName);

													// Create a buffer for reading the files
													final byte[] buf = new byte[1024];

													try
													{
														final String biuf = b.getPath() + ".biuf";

														// Create the ZIP file
														final ZipOutputStream out = new ZipOutputStream(new FileOutputStream(biuf));
														//out.setMethod(ZipOutputStream.DEFLATED);
														out.setLevel(Deflater.NO_COMPRESSION);

														final OutputStreamWriter out2 = new OutputStreamWriter(new FileOutputStream(ArabicIndexer.programFolder + "temp/arabicBook"), StandardCharsets.UTF_8);

														final File[] listOfSubBooks = b.listFiles();
														if (listOfSubBooks != null)
														{
															for (final File sb : listOfSubBooks)
															{
																final String pdf = sb.getName();
																final String subBookName = pdf.split("\\.")[0];

																if (pdf.endsWith(".pdf"))
																{
																	out2.write(translation[3] + subBookName + 'ö' + bookName + 'ö' + category + 'ö' + author + 'ö' + pdf + System.lineSeparator());

																	append(System.lineSeparator() + translation[3] + subBookName + System.lineSeparator());

																	final OutputStreamWriter out3 = new OutputStreamWriter(new FileOutputStream(ArabicIndexer.programFolder + "temp/" + pdf + ".txt"), StandardCharsets.UTF_8);

																	final File[] pages = pdfBox ? convertPdf2Png(sb) : Gs_convertPdf2Png(sb); // to increase dpi

																	append(translation[9]);

																	for (int i = 0; i < pages.length; i++)
																	{
																		try
																		{
																			final String result = tesseract.doOCR(pages[i]);
																			out3.write(result + System.lineSeparator());
																			out3.write("öööööö " + (i + 1) + " öööööö" + System.lineSeparator());
																			pages[i].delete();
																		}
																		catch (TesseractException e)
																		{
																			append(e.getMessage() + System.lineSeparator() + System.lineSeparator());
																			e.printStackTrace();
																		}

																		append("-" + (i+1));
																	}
																	out3.close();

																	out.putNextEntry(new ZipEntry(pdf + ".txt"));
																	FileInputStream in = new FileInputStream(ArabicIndexer.programFolder + "temp/" + pdf + ".txt");
																	int len;
																	while ((len = in.read(buf)) > 0)
																		out.write(buf, 0, len);
																	out.closeEntry();
																	in.close();

																	// Copy pdf file
																	out.putNextEntry(new ZipEntry(pdf));
																	in = new FileInputStream(sb);
																	while ((len = in.read(buf)) > 0)
																		out.write(buf, 0, len);
																	out.closeEntry();
																	in.close();
																}
															}
														}

														out2.close();

														final OutputStreamWriter out1 = new OutputStreamWriter(new FileOutputStream(ArabicIndexer.programFolder + "temp/info"), StandardCharsets.UTF_8);
														out1.write(bookName + System.lineSeparator());
														out1.write(ArabicIndexer.StreamConverter(ArabicIndexer.programFolder + "setting/version.txt")[1] + System.lineSeparator());
														out1.write(translation[4] + author +
																System.lineSeparator() + translation[6] + category +
																System.lineSeparator() + translation[7] + listOfSubBooks.length); // book betakah, for now it is author name + category
														out1.close();

														// Adding 'info' to the zip file
														out.putNextEntry(new ZipEntry("info"));
														FileInputStream in = new FileInputStream(ArabicIndexer.programFolder + "temp/info");
														int len;
														while ((len = in.read(buf)) > 0)
															out.write(buf, 0, len);
														out.closeEntry();
														in.close();

														out.putNextEntry(new ZipEntry("arabicBook"));
														in = new FileInputStream(ArabicIndexer.programFolder + "temp/arabicBook");
														while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
														out.closeEntry();
														in.close();

														out.close(); // Complete the ZIP file
													}
													catch (Exception ex)
													{
														append(ex.getMessage() + System.lineSeparator() + System.lineSeparator());
														ex.printStackTrace();
													}
													append(System.lineSeparator() + "*****************************" + System.lineSeparator());
												}

												// Clear the temp folder
												final File[] deletedFiles = new File(ArabicIndexer.programFolder + "temp/").listFiles();
												for (File element : deletedFiles)
													element.delete();
											}
										}
									}
								}
							}

							browse.setEnabled(true);

							final int duration = (int) ((System.nanoTime() - startTime) / 1000000000.0);
							final String hour = String.valueOf(duration / 3600);
							final String minute = String.valueOf(duration / 60 - (duration / 3600) * 60);
							final String second = String.valueOf(duration - ((int) ((float) duration / 60) * 60));

							append(translation[8] + hour + ':' + minute + ':' + second + System.lineSeparator());
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

	// Converts PDF to PNG format using PdfBox. modified to increase DPI. src from:
	// https://github.com/nguyenq/tess4j/blob/master/src/main/java/net/sourceforge/tess4j/util/PdfBoxUtilities.java
	public static File[] convertPdf2Png(File inputPdfFile) throws IOException
	{
		final Path path = Files.createTempDirectory("tessimages");
		final File imageDir = path.toFile();

		PDDocument document = null;
		try
		{
			document = PDDocument.load(inputPdfFile);
			final PDFRenderer pdfRenderer = new PDFRenderer(document);
			for (int page = 0; page < document.getNumberOfPages(); ++page)
			{
				final BufferedImage bim = pdfRenderer.renderImageWithDPI(page, dpi, ImageType.RGB);

				// suffix in filename will be used as the file format
				final String filename = String.format("workingimage%04d.png", page + 1);
				ImageIOUtil.writeImage(bim, new File(imageDir, filename).getAbsolutePath(), dpi);
			}
		}
		catch (IOException ioe)
		{
			ioe.printStackTrace();
		}
		finally
		{
			final String[] ls = imageDir.list();
			if (ls != null && ls.length == 0)
				imageDir.delete();

			if (document != null)
			{
				try
				{
					document.close();
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		}

		// find working files
		return imageDir.listFiles(new FilenameFilter()
		{
			@Override
			public boolean accept(File dir, String name)
			{
				return name.toLowerCase().matches("workingimage\\d{4}\\.png$");
			}
		});
	}

	// Converts PDF to PNG format using GS. modified to increase DPI. src from:
	// https://github.com/nguyenq/tess4j/blob/master/src/main/java/net/sourceforge/tess4j/util/PdfGsUtilities.java
	public synchronized static File[] Gs_convertPdf2Png(File inputPdfFile) throws IOException
	{
		final Path path = Files.createTempDirectory("tessimages");
		final File imageDir = path.toFile();

		//get Ghostscript instance
		final Ghostscript gs = Ghostscript.getInstance();

		//prepare Ghostscript interpreter parameters
		//refer to Ghostscript documentation for parameter usage
		final java.util.List<String> gsArgs = new ArrayList<>();
		gsArgs.add("-gs");
		gsArgs.add("-dNOPAUSE");
		gsArgs.add("-dQUIET");
		gsArgs.add("-dBATCH");
		gsArgs.add("-dSAFER");
		gsArgs.add("-sDEVICE=pnggray");
		gsArgs.add("-r" + dpi);
		gsArgs.add("-dGraphicsAlphaBits=4");
		//gsArgs.add("-dTextAlphaBits=4"); // Not needed as all of them are scanned pdf files
		gsArgs.add("-sOutputFile=" + imageDir.getPath() + "/workingimage%04d.png");
		gsArgs.add(inputPdfFile.getPath());

		//execute and exit interpreter
		try
		{
			synchronized (gs)
			{
				gs.initialize(gsArgs.toArray(new String[0]));
				gs.exit();
			}
		}
		catch (UnsatisfiedLinkError | GhostscriptException | NoClassDefFoundError e)
		{
			e.printStackTrace();
		}
		finally
		{
			final String[] ls = imageDir.list();
			if (ls != null && ls.length == 0)
				imageDir.delete();

			//delete interpreter instance (safer)
			try
			{
				Ghostscript.deleteInstance();
			}
			catch (GhostscriptException e)
			{
				e.printStackTrace();
			}
		}

		// find working files
		return imageDir.listFiles(new FilenameFilter()
		{
			@Override
			public boolean accept(File dir, String name)
			{
				return name.toLowerCase().matches("workingimage\\d{4}\\.png$");
			}
		});
	}
}