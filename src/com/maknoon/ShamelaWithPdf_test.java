package com.maknoon;

import org.apache.commons.io.FileUtils;

import java.io.*;
import java.sql.*;
import java.util.Collection;
import java.util.Vector;

public class ShamelaWithPdf_test
{
	final String pdfPath = "F:/shamela=PDF12300/pdf";
	final String pdfDB = "C:/Users/Ebrahim/Desktop/migration/special.accdb";
	final String bokFolder = "C:/Users/Ebrahim/Desktop/migration/bok";

	public static void main(String[] args)
	{
		try
		{
			new ShamelaWithPdf_test();
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}

	public ShamelaWithPdf_test() throws Exception
	{
		// Collect all pdf files path
		final Collection<File> pdfFiles = FileUtils.listFiles(
				new File(pdfPath),
				new String[]{"pdf"},
				true
		);
		final Vector<String> pdfPaths = new Vector<>(pdfFiles.size());
		for (File f : pdfFiles)
			pdfPaths.add(f.getAbsolutePath());

		final Vector<String> oNum_oPdf = new Vector<>(5000, 500);
		final Vector<String> BkId_0pdf = new Vector<>(5000, 500);
		final Vector<String> PdfPath_oPdf = new Vector<>(5000, 500);
		final Vector<String> PdfPath_0pdf = new Vector<>(5000, 500);

		final Connection conPDF = DriverManager.getConnection("jdbc:ucanaccess://" + pdfDB + ";memory=false;singleconnection=true");
		final Statement stmtPDF = conPDF.createStatement();

		final Vector<String> dbPdfPaths = new Vector<>(15000);
		ResultSet rsPDF1 = stmtPDF.executeQuery("SELECT * FROM oPdf");
		while (rsPDF1.next())
		{
			final String pdf = rsPDF1.getString("PdfPath");
			final String path = pdf.replaceFirst("Rel:pdf", pdfPath);
			dbPdfPaths.add(new File(path).getCanonicalPath());
		}
		rsPDF1.close();
		rsPDF1 = stmtPDF.executeQuery("SELECT * FROM 0pdf");
		while (rsPDF1.next())
		{
			final String pdf = rsPDF1.getString("PdfPath");
			final String path = pdf.replaceFirst("Rel:pdf", pdfPath);
			dbPdfPaths.add(new File(path).getCanonicalPath());
		}
		rsPDF1.close();
		stmtPDF.close();
		conPDF.close();

		// Display the pdf files that has no entries in the DB
		for (String s : pdfPaths)
		{
			boolean found = false;
			for (String j : dbPdfPaths)
			{
				if(s.equals(j))
				{
					found = true;
					break;
				}
			}

			if(!found)
				System.out.println(s + System.lineSeparator());
		}

		if(true)
			return;

		System.out.println("oPdf" + System.lineSeparator());

		// TEST 1: Check if Paths are valid and has PDF files
		final ResultSet rsPDF = stmtPDF.executeQuery("SELECT * FROM oPdf");
		while (rsPDF.next())
		{
			final String pdf = rsPDF.getString("PdfPath");
			final String path = pdf.replaceFirst("Rel:pdf", pdfPath);

			if (!new File(path).exists())
				System.out.println(rsPDF.getString("oNum") + " " + pdf + System.lineSeparator());
			else
			{
				oNum_oPdf.add(rsPDF.getString("oNum"));
				PdfPath_oPdf.add(pdf);
			}
		}
		rsPDF.close();

		System.out.println("0pdf" + System.lineSeparator());

		final ResultSet rsPDF2 = stmtPDF.executeQuery("SELECT * FROM 0pdf");
		while (rsPDF2.next())
		{
			final String pdf = rsPDF2.getString("PdfPath");
			final String path = pdf.replaceFirst("Rel:pdf", pdfPath);

			if (!new File(path).exists())
				System.out.println(rsPDF2.getString("BkId") + " " + pdf + System.lineSeparator());
			else
			{
				BkId_0pdf.add(rsPDF2.getString("BkId"));
				PdfPath_0pdf.add(pdf);
			}
		}
		rsPDF2.close();

		final Thread[] threads = new Thread[4];
		for (int k = 0; k < threads.length; k++)
		{
			final int q = k;
			threads[q] = new Thread()
			{
				public void run()
				{
					try
					{
						final File[] files = new File(bokFolder + q).listFiles();
						if(files != null)
						{
							for (final File f : files)
							{
								// TEST 2: Check all *.bok files and if BkId != oNum, then check manually from the output if book name related to its pdf path
								final Connection con = DriverManager.getConnection("jdbc:ucanaccess://" + f + ";memory=false;singleconnection=true");
								final Statement stmt1 = con.createStatement();
								final ResultSet rs1 = stmt1.executeQuery("SELECT * FROM Main");
								while (rs1.next())
								{
									final String bookName = rs1.getString("Bk");
									final String bookId = rs1.getString("BkId");
									final String oNum = rs1.getString("oNum");

									if (oNum == null || oNum.isEmpty())
										continue;

									if (!bookId.equals(oNum))
									{
										System.out.println("oNum = " + oNum + " bookId = " + bookId + System.lineSeparator());
										System.out.println(bookName + System.lineSeparator());
										System.out.println(f + System.lineSeparator());

										int i = oNum_oPdf.indexOf(oNum);
										if (i != -1)
											System.out.println("oPdf: "+PdfPath_oPdf.elementAt(i) + System.lineSeparator());

										int k = BkId_0pdf.indexOf(bookId);
										if (k != -1)
											System.out.println("0pdf: "+PdfPath_0pdf.elementAt(k) + System.lineSeparator());

										System.out.println(System.lineSeparator() + "*****************************" + System.lineSeparator());
									}
								}

								rs1.close();
								stmt1.close();
								con.close();
							}
						}
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