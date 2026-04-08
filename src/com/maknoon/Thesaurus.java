package com.maknoon;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Thesaurus
{
    public static void main(String[] args)
    {
        final Vector<Vector<String>> thesaurus = new Vector<>(5000, 100);
        try
        {
            final BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream("E:/ArabicIndexer Media/th_ar.txt"), StandardCharsets.UTF_8));
            final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream("Thesaurus.txt"), StandardCharsets.UTF_8);
            while (in.ready())
            {
                final Vector<String> words = new Vector<>();
                final StringTokenizer word_tokens = new StringTokenizer(in.readLine(), "|");
                words.add(Clean(word_tokens.nextToken()));
                //out.write(word_tokens.nextToken()+"\t");
                final int meaning_count = Integer.parseInt(word_tokens.nextToken());
                for (int i = 0; i < meaning_count; i++)
                {
                    final StringTokenizer meaning_tokens = new StringTokenizer(in.readLine(), "|");
                    meaning_tokens.nextToken(); // Description of the meaning
                    while (meaning_tokens.hasMoreTokens())
                        words.add(Clean(meaning_tokens.nextToken()));
                    //out.write(meaning_tokens.nextToken()+"\t");
                }
                //out.write(lineSeparator);
                thesaurus.add(words);
            }

            for (int i = 0; i < thesaurus.size(); i++)
            {
                final Vector<String> words = thesaurus.elementAt(i);

                // Remove multi-words (with spaces)
                for (int j = 0; j < words.size(); j++)
                {
                    if (words.elementAt(j).contains(" "))
                    {
                        words.removeElementAt(j);
                        j--;
                    }
                }

                // Remove the duplicates
                for (int j = 0; j < words.size(); j++)
                {
                    for (int q = j + 1; q < words.size(); q++)
                    {
                        if (words.elementAt(j).equals(words.elementAt(q)))
                        {
                            words.removeElementAt(q);
                            q--;
                        }
                    }
                }

                if (words.size() > 1)
                    out.write(words.toString() + System.lineSeparator());
            }
            out.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static String Clean(String line)
    {
        if (line.indexOf('(') > -1)
            line = line.substring(0, line.indexOf('(')).trim();

        if (line.indexOf('-') > -1)
            line = line.substring(0, line.indexOf('-')).trim();

        return line;
    }
}