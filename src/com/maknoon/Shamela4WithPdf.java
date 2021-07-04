package com.maknoon;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpHost;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.sqlite.SQLiteConfig;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Shamela4WithPdf
{
	final String pdfPath = "F:/shamela4/pdf";
	final String masterDB = "F:/shamela4/database/master.db";
	final String dbFolder = "F:/shamela4/database/book";
	final String biufFolder = "C:/Users/Ebrahim/Desktop/migration/biuf-v4";

	public static void main(String[] args)
	{
		try
		{
			final Logger databaseLogger = Logger.getLogger("shamela4.log");
			databaseLogger.setUseParentHandlers(false);
			databaseLogger.setLevel(Level.WARNING);
			databaseLogger.addHandler(new FileHandler("shamela4.log"));

			new Shamela4WithPdf();
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}

	RestHighLevelClient client;
	public Shamela4WithPdf() throws Exception
	{
		Class.forName("org.sqlite.JDBC");

		final String programFolder = new File(Shamela4WithPdf.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getAbsolutePath() + File.separator;
		String biuf_version = "";
		try
		{
			final Properties prop = new Properties();
			prop.load(new FileInputStream(programFolder + "setting/setting.properties"));
			biuf_version = prop.getProperty("biuf_version");
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		final SQLiteConfig config = new SQLiteConfig();
		config.setEncoding(SQLiteConfig.Encoding.UTF8);

		final String[] translation = ArabicIndexer.StreamConverter("language/ShamelaConvertor.txt");

		// Collect all pdf files path
		final Collection<File> pdfFiles = FileUtils.listFiles(
				new File(pdfPath),
				new String[]{"pdf"},
				true
		);

		// Collect all db files path
		final Collection<File> dbFiles = FileUtils.listFiles(
				new File(dbFolder),
				new String[]{"db"},
				true
		);
		final Vector<String> dbName = new Vector<>(dbFiles.size());
		final Vector<File> dbFile = new Vector<>(dbFiles.size());
		for (File f : dbFiles)
		{
			dbName.add(f.getName());
			dbFile.add(f);
		}

		client = new RestHighLevelClient(
				RestClient.builder(
						new HttpHost("localhost", 7543, "http"),
						new HttpHost("localhost", 7543, "http")));

		final Connection conMaster = DriverManager.getConnection("jdbc:sqlite:" + masterDB, config.toProperties());
		final Statement stmtMaster = conMaster.createStatement();
		final Statement stmtMaster2 = conMaster.createStatement();

		final ResultSet rsPDF = stmtMaster.executeQuery("SELECT * FROM book where pdf_links NOT NULL AND book_id = 1727");
		while (rsPDF.next())
		{
			final String pdf_links = rsPDF.getString("pdf_links");
			final String book_name = rsPDF.getString("book_name");
			final int book_id = rsPDF.getInt("book_id");
			final int authors = rsPDF.getInt("authors");
			final int book_category = rsPDF.getInt("book_category");
			final JSONObject obj = new JSONObject(pdf_links);

			if(!obj.has("files"))
				continue;

			final JSONArray files = obj.getJSONArray("files");
			final Vector<File> bookPdfFiles = new Vector<>();
			final Vector<String> bookPdfNames = new Vector<>();

			String author = "";
			ResultSet rs5 = stmtMaster2.executeQuery("SELECT author_name FROM author WHERE author_id = " + authors);
			if (rs5.next())
				author = rs5.getString("author_name");
			rs5.close();

			String category = "";
			rs5 = stmtMaster2.executeQuery("SELECT category_name FROM category WHERE category_id = " + book_category);
			if (rs5.next())
				category = rs5.getString("category_name");
			rs5.close();

			for (int i = 0; i < files.length(); i++)
			{
				final String[] token = files.getString(i).split("[\\|]+"); // instead of "\\|" to remove empty spaces in the middle since some string has double '||'
				String pdf = token[0];
				if (pdf.startsWith("http"))
				{
					final String[] q = pdf.split("/");
					pdf = q[q.length - 1]; // to get the file name which is mostly is the one stored on disk
				}

				boolean found = false;
				for (File f : pdfFiles)
				{
					if (f.getParentFile().getName().equals(book_name) && f.getName().equals(pdf))
					{
						bookPdfFiles.add(f);
						if (token.length == 2)
							bookPdfNames.add(token[1]); // part name
						else
							bookPdfNames.add("");
						found = true;
						break;
					}
				}

				if(!found) // some book_name has a folder with a different name
				{
					System.out.println("book name is not matched with any folder ! book_id = " + book_id);
					for (File f : pdfFiles)
					{
						if (f.getName().equals(pdf)) // it will take the first one, in case, there is another one, it might be wrong
						{
							bookPdfFiles.add(f);
							if (token.length == 2)
								bookPdfNames.add(token[1]); // part name
							else
								bookPdfNames.add("");
							break;
						}
					}
				}
			}

			final Path tempDir = Files.createTempDirectory("shamela_");

			// Create a buffer for reading the files.
			final byte[] buf = new byte[1024];

			// Create the ZIP file
			final ZipOutputStream out = new ZipOutputStream(new FileOutputStream(biufFolder + "/" + book_name.replaceAll(":", " ") + '_' + book_id + ".biuf"));

			final OutputStreamWriter out1 = new OutputStreamWriter(new FileOutputStream(new File(tempDir.toFile(), "info")), StandardCharsets.UTF_8);
			out1.write(book_name + System.lineSeparator());
			out1.write(biuf_version + System.lineSeparator());
			//out1.write(rs1.getString("Betaka"));
			out1.close();

			// Adding 'info' to the zip file.
			out.putNextEntry(new ZipEntry("info"));
			FileInputStream in = new FileInputStream(new File(tempDir.toFile(), "info"));
			int len;
			while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
			out.closeEntry();
			in.close();

			final File db = dbFile.get(dbName.indexOf(book_id + ".db"));
			final Connection conDb = DriverManager.getConnection("jdbc:sqlite:" + db, config.toProperties());
			final Statement stmtDb = conDb.createStatement();
			final Statement stmtDb1 = conDb.createStatement();

			boolean dbWithAlias = false;
			final ResultSet rs8 = stmtDb.executeQuery("select count(*) as size FROM sqlite_master WHERE type='table' and name='alias'");
			if (rs8.next())
			{
				dbWithAlias = rs8.getInt("size") == 1;
				if(dbWithAlias)
					System.out.println("db with alias, book_id " + book_id);
			}
			rs8.close();

			int max = 0;
			final ResultSet rs7 = stmtDb.executeQuery("SELECT COUNT(part) AS size FROM (SELECT part FROM page WHERE part IS NOT NULL AND page IS NOT NULL GROUP BY part)");
			if (rs7.next())
				max = rs7.getInt("size");
			rs7.close();

			final OutputStreamWriter out2 = new OutputStreamWriter(new FileOutputStream(new File(tempDir.toFile(), "arabicBook")), StandardCharsets.UTF_8);

			final HashSet<String> zipFilesList = new HashSet<>();
			if (max == 0 || max == 1) // part is NULL or just one volume
			{
				final Vector<Integer> id_t = new Vector<>();
				final Vector<Integer> page_t = new Vector<>();
				final Vector<Integer> alias_book_id = new Vector<>();
				int max_page = 0;
				String part = null;

				// Single-volume book
				ResultSet rs2;
				if(dbWithAlias)
					rs2 = stmtDb.executeQuery("select id, part, page, book_id, page_id from page left join alias on id = this_id WHERE page NOT NULL ORDER BY id");
				else
					rs2 = stmtDb.executeQuery("SELECT id, part, page FROM page WHERE page NOT NULL ORDER BY id");

				while (rs2.next())
				{
					part = rs2.getString("part"); // in rare case, it has a value that needs to be used to fetch the pdf file
					int page = rs2.getInt("page");
					page_t.add(page);
					if (page > max_page)
						max_page = page;

					if (dbWithAlias)
					{
						int page_id = rs2.getInt("page_id");
						if(page_id != 0)
						{
							id_t.add(page_id);
							alias_book_id.add(rs2.getInt("book_id"));
						}
						else
						{
							id_t.add(rs2.getInt("id"));
							alias_book_id.add(book_id);
						}
					}
					else
					{
						id_t.add(rs2.getInt("id"));
						alias_book_id.add(book_id);
					}
				}
				rs2.close();

				final Vector<String> pages = new Vector<>();
				pages.setSize(max_page);

				elastic(pages, alias_book_id, id_t, page_t, part);

				File pdf = null;
				if (bookPdfFiles.size() == 1)
					pdf = bookPdfFiles.elementAt(0);
				else
				{
					for (int o = 0; o < bookPdfFiles.size(); o++)
					{
						final String name = bookPdfFiles.elementAt(o).getName();
						if(!name.endsWith("p.pdf") && !name.endsWith("m.pdf") && !name.contains("_"))
						{
							pdf = bookPdfFiles.elementAt(o);
							break;
						}

						if(name.contains("_") && part != null)
						{
							final int p = Integer.parseInt(part);
							final DecimalFormat formatter = new DecimalFormat("00");
							final String pFormatted = formatter.format(p);
							if(name.startsWith(pFormatted))
							{
								pdf = bookPdfFiles.elementAt(o);
								break;
							}
						}
					}
				}

				if(pdf != null)
				{
					final String txtFile = book_id + ".txt";

					out2.write(book_name + "öö" + category + "ö" + author + "ö" + pdf.getName() + "ö" + txtFile + System.lineSeparator());
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

					out.putNextEntry(new ZipEntry(pdf.getName()));
					in = new FileInputStream(pdf);
					while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
					out.closeEntry();
					in.close();

					System.out.println(translation[5] + pdf.getName() + System.lineSeparator());
				}
				else
					System.out.println("Single volume book but no pdf associated ! book_id = " + book_id);
			}
			else
			{
				final Vector<Integer> id_t = new Vector<>();
				final Vector<Integer> page_t = new Vector<>();
				final Vector<Integer> alias_book_id = new Vector<>();

				/*
				boolean multiVolumeSinglePdf = false;

				// In case multiple volumes but same pdf file for all of them i.e. pages are continuous
				if(max > 2 && bookPdfFiles.size() <= 2)
				{
					System.out.println("Multi volume book but only 1 pdf file ! book_id = " + book_id);
					multiVolumeSinglePdf = true;
				}
				*/

				// Multi-volume book
				ResultSet rs9;
				if(dbWithAlias)
					rs9 = stmtDb.executeQuery("select part, MAX(MAX(coalesce(page_id, 0)), MAX(coalesce(page, 0))) AS size FROM page LEFT JOIN alias on id = this_id WHERE part NOT NULL AND page NOT NULL GROUP BY part ORDER BY id");
				else
					rs9 = stmtDb.executeQuery("SELECT part, MAX(page) AS size FROM page WHERE part NOT NULL AND page NOT NULL GROUP BY part ORDER BY id"); // 'ORDER BY id' to make sure that parts in order

				while (rs9.next())
				{
					id_t.clear();
					page_t.clear();
					alias_book_id.clear();

					final String part = rs9.getString("part");
					final int size = rs9.getInt("size");
					final Vector<String> pages = new Vector<>();
					pages.setSize(size);

					ResultSet rs2;
					if(dbWithAlias)
						rs2 = stmtDb1.executeQuery("SELECT id, part, page, book_id, page_id FROM page LEFT JOIN alias on id = this_id WHERE part = '"+part+"' AND page >= 1 ORDER BY id");
					else
						rs2 = stmtDb1.executeQuery("SELECT id, part, page FROM page WHERE part = '" + part + "' AND page >= 1 ORDER BY id");

					while (rs2.next())
					{
						page_t.add(rs2.getInt("page"));
						if (dbWithAlias)
						{
							int page_id = rs2.getInt("page_id");
							if(page_id != 0)
							{
								id_t.add(page_id);
								alias_book_id.add(rs2.getInt("book_id"));
							}
							else
							{
								id_t.add(rs2.getInt("id"));
								alias_book_id.add(book_id);
							}
						}
						else
						{
							id_t.add(rs2.getInt("id"));
							alias_book_id.add(book_id);
						}
					}
					rs2.close();

					elastic(pages, alias_book_id, id_t, page_t, part);

					File pdf = null;
					for (int o = 0; o < bookPdfNames.size(); o++)
					{
						if(part.equals(bookPdfNames.elementAt(o))) // for use cases e.g. part = '5 جـ'
						{
							pdf = bookPdfFiles.elementAt(o);
							bookPdfFiles.removeElementAt(o);
							bookPdfNames.removeElementAt(o);
							break;
						}
					}

					if(pdf == null)
					{
						if (part.equals("مقدمة") || part.equals("تقديم") || part.equals("المقدمة"))
						{
							for (int o = 0; o < bookPdfFiles.size(); o++)
							{
								final String bn = bookPdfFiles.elementAt(o).getName();
								if (bn.endsWith("p.pdf"))
								{
									pdf = bookPdfFiles.elementAt(o);
									bookPdfFiles.removeElementAt(o);
									bookPdfNames.removeElementAt(o);
									break;
								}
							}
						}
						else
						{
							if (part.equals("الكتاب")) // expected only مقدمة and الكتاب only 2 parts
							{
								for (int o = 0; o < bookPdfFiles.size(); o++)
								{
									final String bn = bookPdfFiles.elementAt(o).getName();
									if (!bn.endsWith("p.pdf"))
									{
										pdf = bookPdfFiles.elementAt(o);
										bookPdfFiles.removeElementAt(o);
										bookPdfNames.removeElementAt(o);
										break;
									}
								}
							}
							else
							{
								if (bookPdfFiles.size() == 1) // it is only one book left so fit it to all remaining parts, mostly it will be multi volume in single pdf file !
								{
									//if (p == 1)
									{
										pdf = bookPdfFiles.elementAt(0);
										//bookPdfFiles.removeElementAt(0);
										System.out.println("Part " + part + " took the last (or only) pdf file ! book_id = " + book_id + " pdf: " + pdf.getName());
									}
								}
								else
								{
									try
									{
										final int p = Integer.parseInt(part);
										final DecimalFormat formatter = new DecimalFormat("00");
										final String pFormatted = formatter.format(p);

										for (int o = 0; o < bookPdfFiles.size(); o++)
										{
											final String bn = bookPdfFiles.elementAt(o).getName();
											final String bk = bookPdfNames.elementAt(o);
											if ((bn.equals(part + ".pdf")
													|| bn.startsWith(pFormatted+"-")
													|| bn.startsWith(pFormatted+"_")
													|| bn.startsWith(p+"_")
													|| bn.startsWith(p+"-")
													|| (bn.endsWith("-"+p + ".pdf"))
													|| (bn.endsWith("_"+p + ".pdf"))
													|| (bn.endsWith("_"+pFormatted + ".pdf"))
													|| (bn.endsWith("-"+pFormatted + ".pdf"))
													|| (bn.endsWith(p + ".pdf") && !bn.contains("_") && !bn.contains("-")))
													&& !bn.endsWith("p.pdf")
													&& (bk.isEmpty() || bk.contains("#"))
											)
											{
												pdf = bookPdfFiles.elementAt(o);
												bookPdfFiles.removeElementAt(o);
												bookPdfNames.removeElementAt(o);
												break;
											}
										}

										if(pdf == null) // ["37408.pdf", "37408p.pdf|"]
										{
											for (int o = 0; o < bookPdfFiles.size(); o++) // remove all *p.pdf since it has no part associated with it
											{
												final String bn = bookPdfFiles.elementAt(o).getName();
												if (bn.endsWith("p.pdf") || bn.endsWith("m.pdf"))
												{
													bookPdfFiles.removeElementAt(o);
													bookPdfNames.removeElementAt(o);
												}

												if (bookPdfNames.elementAt(o).equals("0")) // '0' in all cases means the book contain many parts e.g. ["badae_p1.pdf|التقديم", "badae_p2.pdf|المقدمة", "badae_1-4.pdf|0", "badae_5.pdf"] ["tafseer_terefi.pdf|0", "tafseer_terefi_i.pdf"]
												{
													pdf = bookPdfFiles.elementAt(o);
													System.out.println("Part " + part + " took the pdf file which has tag '0' ! book_id = " + book_id);
													break;
												}
											}

											if (pdf == null && bookPdfFiles.size() == 1) // it is only one book left so fit it to all remaining parts, mostly it will be multi volume in single pdf file !
											{
												pdf = bookPdfFiles.elementAt(0);
												System.out.println("Part " + part + " took the last (after cleaning) pdf file ! book_id = " + book_id + " pdf: " + pdf.getName());
											}
										}
									}
									catch (NumberFormatException e)
									{
										e.printStackTrace();
										System.out.println("No match, Part " + part + " book_id = " + book_id);
									}
								}
							}
						}
					}

					if (pdf == null)
						System.out.println("Couldn't find pdf for the part "+part+" book_id = " + book_id);
					else
					{
						final String name = book_id + "_" + part;
						final String txtFile = name + ".txt";

						out2.write(translation[4] + part + "ö" + book_name + "ö" + category + "ö" + author + "ö" + pdf.getName() + "ö" + txtFile + System.lineSeparator());
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

						System.out.println(translation[4] + part + "\t->\t" + translation[5] + pdf.getName() + (part.contains("/") ? "\t->\t" + translation[10] + part : "") + System.lineSeparator());
					}
				}
				rs9.close();
			}

			out2.close();

			out.putNextEntry(new ZipEntry("arabicBook"));
			in = new FileInputStream(new File(tempDir.toFile(), "arabicBook"));
			while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
			out.closeEntry();
			out.close(); // Complete the ZIP file
			in.close();

			stmtDb.close();
			conDb.close();

			// Clear the temp folder
			tempDir.toFile().delete();
		}
		rsPDF.close();
		stmtMaster.close();
		stmtMaster2.close();
		conMaster.close();
		client.close();
	}

	void elastic(Vector<String> pages, Vector<Integer> book_id, Vector<Integer> id_t, Vector<Integer> page_t, String part) throws IOException
	{
		for (int id = 0; id < id_t.size(); id++)
		{
			final String id_tmp = "p-" + book_id.get(id) + "-" + id_t.get(id);
			final GetRequest request = new GetRequest("page", id_tmp);
			final GetResponse response = client.get(request, RequestOptions.DEFAULT);
			if (response.isExists())
			{
				final String c = response.getSourceAsString()
						.replaceAll("<.*?>", "")
						.replaceAll("\\\\r", "ö");

				final JSONObject ob = new JSONObject(c);
				final String str = ob.getString("page").replaceAll("ö", System.lineSeparator());

				//final Map<String, Object> source = response.getSource();
				//if (source != null)
				{
					//String str = (String) source.get("page"); // cannot handle '\r'
					//str = str.replaceAll("<.*?>", "");

					final String page = pages.get(page_t.get(id) - 1);
					if (page == null)
						pages.set(page_t.get(id) - 1, str); // response.getField("page").getValue()
					else
						pages.set(page_t.get(id) - 1, page + System.lineSeparator() + str);
				}
			}

			final String id_tmp2 = "f-" + book_id.get(id) + "-" + id_t.get(id);
			final GetRequest request2 = new GetRequest("page", id_tmp2);
			final GetResponse response2 = client.get(request2, RequestOptions.DEFAULT);
			if (response2.isExists())
			{
				final String c = response2.getSourceAsString()
						.replaceAll("<.*?>", "")
						.replaceAll("\\\\r", "ö");

				final JSONObject ob = new JSONObject(c);
				final String str = ob.getString("page").replaceAll("ö", System.lineSeparator());

				final String page2 = pages.get(page_t.get(id) - 1);
				if (page2 == null)
					pages.set(page_t.get(id) - 1, str); // response.getField("page").getValue()
				else
					pages.set(page_t.get(id) - 1, page2 + System.lineSeparator() + "---------------" + System.lineSeparator() + str);
			}
		}
	}
}