package luceneGazateer;
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//package src.main.java.edu.usc.ir.geo.gazetteer;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

class EntryData{
	
	private String id;
	private String name;
	private double score;
	
	public String getId() {
		return id;
	}
	
	public String getName() {
		return name;
	}
	
	public double getScore() {
		return score;
	}
	
	public static EntryData getInstance(String id, String n, double s){
		EntryData newEntry = new EntryData();
		newEntry.id = id;
		newEntry.name = n;
		newEntry.score = s;
		return newEntry;
	}
}

public class GeoNameResolver {
	private static final Logger LOG = Logger.getLogger(GeoNameResolver.class
			.getName());
	private static final Double OUT_OF_BOUNDS = 999999.0;
	private static Analyzer analyzer = new StandardAnalyzer();
	private static IndexWriter indexWriter;
	private static Directory indexDir;
	private static int hitsPerPage = 100
			;
	private static DocType globalDocType = DocType.ELEC_SUPP;
	
	static class Tuple<X, Y>{
		public final X x;
		public final Y y;
		public Tuple(X x, Y y){
			this.x = x;
			this.y = y;
		}
	}

	/**
	 * Search corresponding GeoName for each location entity
	 * 
	 * @param querystr
	 *            it's the NER actually
	 * @return HashMap each name has a list of resolved entities
	 * @throws IOException
	 * @throws RuntimeException
	 */
	public enum DocType{
		ELEC_PART, ELEC_SUPP	
	}
	public ArrayList<EntryData> searchDocuments(String indexerPath, 
			String inputRecord, DocType recordType) throws IOException {

		File indexfile = new File(indexerPath);
		indexDir = FSDirectory.open(indexfile.toPath());

		//inputRecord.replace(","," ");
		if (!DirectoryReader.indexExists(indexDir)) {
			LOG.log(Level.SEVERE,
					"No Lucene Index Dierctory Found, Invoke indexBuild() First !");
			System.out.println("No Lucene Index Dierctory Found, Invoke indexBuild() First !");
			System.exit(1);
		}

		IndexReader reader = DirectoryReader.open(indexDir);

		IndexSearcher searcher = new IndexSearcher(reader);

		Query q = null;

		HashMap<String, ArrayList<ArrayList<String>>> allCandidates = new HashMap<String, ArrayList<ArrayList<String>>>();

		if (!allCandidates.containsKey(inputRecord)) {
			try {
				ArrayList<ArrayList<String>> topHits = new ArrayList<ArrayList<String>>();
				//System.out.println("query is : "+inputRecord);
				q = new MultiFieldQueryParser(new String[] { "DATA" }, analyzer).parse(inputRecord);
			
				TopScoreDocCollector collector = TopScoreDocCollector
						.create(hitsPerPage);
				searcher.search(q, collector);
				ScoreDoc[] hits = collector.topDocs().scoreDocs;
				for (int i = 0; i < hits.length; ++i) {
					ArrayList<String> tmp1 = new ArrayList<String>();
					int docId = hits[i].doc;
					Document d;
					try {
						d = searcher.doc(docId);
						tmp1.add(d.get("ID"));
						tmp1.add(d.get("DATA"));
						tmp1.add(((Float)hits[i].score).toString());

					} catch (IOException e) {
						e.printStackTrace();
					}
					topHits.add(tmp1);
				}
				allCandidates.put(inputRecord, topHits);
			} catch (org.apache.lucene.queryparser.classic.ParseException e) {
				e.printStackTrace();
			}
		}

		ArrayList<EntryData> resolvedEntities = new ArrayList<EntryData>();
		pickBestCandidates(resolvedEntities, allCandidates);
		reader.close();

		return resolvedEntities;

	}

	/**
	 * Select the best match for each location name extracted from a document,
	 * choosing from among a list of lists of candidate matches. Filter uses the
	 * following features: 1) edit distance between name and the resolved name,
	 * choose smallest one 2) content (haven't implemented)
	 * 
	 * @param resolvedEntities
	 *            final result for the input stream
	 * @param allCandidates
	 *            each location name may hits several documents, this is the
	 *            collection for all hitted documents
	 * @throws IOException
	 * @throws RuntimeException
	 */

	private void pickBestCandidates(
			ArrayList<EntryData> resolvedEntities,
			HashMap<String, ArrayList<ArrayList<String>>> allCandidates) {
		//System.out.println("all candidates:"+ allCandidates.size());
		for (String extractedName : allCandidates.keySet()) {
			ArrayList<ArrayList<String>> cur = allCandidates.get(extractedName);
			int minDistance = Integer.MAX_VALUE, minIndex = -1;
			for (ArrayList<String> entry: cur){
				resolvedEntities.add(EntryData.getInstance(entry.get(0), entry.get(1), Double.parseDouble(entry.get(2))));
			}
			
		}
	}

	/**
	 * Build the gazetteer index line by line
	 * 
	 * @param gazetteerPath
	 *            path of the gazetteer file
	 * @param indexerPath
	 *            path to the created Lucene index directory.
	 * @throws IOException
	 * @throws RuntimeException
	 */
	public void buildIndex(String gazetteerPath, String indexerPath)
			throws IOException {
		File indexfile = new File(indexerPath);
		indexDir = FSDirectory.open(indexfile.toPath());
		if (!DirectoryReader.indexExists(indexDir)) {
			IndexWriterConfig config = new IndexWriterConfig(analyzer);
			indexWriter = new IndexWriter(indexDir, config);
			Logger logger = Logger.getLogger(this.getClass().getName());
			logger.log(Level.WARNING, "Start Building Index for Gazatteer");
			BufferedReader filereader = new BufferedReader(
					new InputStreamReader(new FileInputStream(gazetteerPath),
							"UTF-8"));
			String line;
			int count = 0;
			while ((line = filereader.readLine()) != null) {
				try {
					count += 1;
					if (count % 100000 == 0) {
						logger.log(Level.INFO, "Indexed Row Count: " + count);
					}
					addDoc(indexWriter, line);

				} catch (RuntimeException re) {
					logger.log(Level.WARNING, "Skipping... Error on line: {}",
							line);
				}
			}
			logger.log(Level.WARNING, "Building Finished");
			filereader.close();
			indexWriter.close();
		}
	}

	/**
	 * Index gazetteer's one line data by built-in Lucene Index functions
	 * 
	 * @param indexWriter
	 *            Lucene indexWriter to be loaded
	 * @param line
	 *            a line from the gazetteer file
	 * @throws IOException
	 * @throws NumberFormatException
	 */
	private static void addDoc(IndexWriter indexWriter, String line) {
		String[] tokens = line.split("\t");
		//changed to work with our dataset
		String id = tokens[0];
		String data = tokens[1];
		
		System.out.println(data);
		Document doc = new Document();
		doc.add(new TextField("ID", id, Field.Store.YES));
		doc.add(new TextField("DATA", data, Field.Store.YES));
		try {
			indexWriter.addDocument(doc);
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
	
	public static void produceCandidates(String indexPath, String fileName
					, GeoNameResolver resolver) throws IOException{
		BufferedReader filereader = new BufferedReader(
					new InputStreamReader(new FileInputStream(fileName),
							"UTF-8"));
		FileWriter ps = new FileWriter("output.txt", true);
		String line = "";		
		String testString = "";
		String uri = "";
		while ((line = filereader.readLine()) != null) {
			String[] locArgs = line.split("\t");
			try{
				uri = locArgs[0];
				testString = locArgs[1];
				System.out.println(testString);
			}catch(IndexOutOfBoundsException e){
				e.printStackTrace();
				System.exit(-1);
			}
			
			
			
			ArrayList<EntryData> resolved = resolver
				.searchDocuments(indexPath,
						testString, globalDocType);
			
			resolved.sort(new Comparator<EntryData>(){
                @Override
                public int compare(EntryData e1, EntryData e2) {
                    Double diff = e1.getScore() - e2.getScore();
                    if(diff < 1e-6 && diff > -1e-6)
                        return 0;
                    else if(diff > 0)
                        return -1;
                    return 1;
                }
            });
            ps.write("{\"query_string\":{\"uri\":\""+ uri 
						+ "\",\"name\":\"" + testString  + "\", \"candidates\":");
            ps.write("[");
            for(int i = 0 ; i < resolved.size() ; i++){
            //for(Map.Entry<String, Double> entry : candidatesEntryList){
            	ps.write("{\"uri\":\"" + resolved.get(i).getId() + "\",");
				ps.write("\"name\":\"" + resolved.get(i).getName() + "\",");
				ps.write("\"score\":\"" + resolved.get(i).getScore() + "\"");
				
				if (i < resolved.size() -1){
					ps.write("},");						
				}
				else{
					ps.write("}");
				}
                //System.out.println("\t" + entry.getValue() + "\t" + entry.getKey());
            }
            ps.write("]}}\n");
			ps.flush();
			
		}
	}

	public static void main(String[] args) throws IOException {
		Option buildOpt = OptionBuilder.withArgName("gazetteer file").hasArg().withLongOpt("build")
				.withDescription("The Path to the Geonames allCountries.txt")
				.create('b');

		Option searchOpt = OptionBuilder.withArgName("set of location names").withLongOpt("search").hasArgs()
				.withDescription("Location names to search the Gazetteer for")
				.create('s');

		Option indexOpt = OptionBuilder
				.withArgName("directoryPath")
				.withLongOpt("index")
				.hasArgs()
				.withDescription(
						"The path to the Lucene index directory to either create or read")
				.create('i');

		Option helpOpt = OptionBuilder.withLongOpt("help")
				.withDescription("Print this message.").create('h');

		String indexPath = null;
		String gazetteerPath = null;
		ArrayList<String> geoTerms = null;
		Options options = new Options();
		options.addOption(buildOpt);
		options.addOption(searchOpt);
		options.addOption(indexOpt);
		options.addOption(helpOpt);

		// create the parser
		CommandLineParser parser = new DefaultParser();
		GeoNameResolver resolver = new GeoNameResolver();

		try {
			// parse the command line arguments
			CommandLine line = parser.parse(options, args);

			if (line.hasOption("index")) {
				indexPath = line.getOptionValue("index");
			}

			if (line.hasOption("build")) {
				gazetteerPath = line.getOptionValue("build");
			}

			if (line.hasOption("help")) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp("lucene-geo-gazetteer", options);
				System.exit(1);
			}

			if (indexPath != null && gazetteerPath != null) {
				LOG.info("Building Lucene index at path: [" + indexPath
						+ "] with geoNames.org file: [" + gazetteerPath + "]");
				resolver.buildIndex(gazetteerPath, indexPath);
			}

			if (line.hasOption("search")) {
				String temp_s = "";
				for (String string : line.getOptionValues("search")) {
					temp_s += string;
				}
				
				produceCandidates(indexPath, temp_s, resolver);
			}

		} catch (ParseException exp) {
			// oops, something went wrong
			System.err.println("Parsing failed.  Reason: " + exp.getMessage());
		}
	}

}
