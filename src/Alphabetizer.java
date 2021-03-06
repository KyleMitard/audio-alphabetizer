/**
 * Alphabetizer.java
 * 
 * A class that splices and joins audio in such a way that all of the words are in alphabetical order
 * 
 * @author Kyle Mitard
 * 
 * Created 3 May 2020
 * Updated 4 May 2020
 */

//java imports
import java.io.IOException;
import java.net.MalformedURLException;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;

//CMUSphinx imports
import edu.cmu.sphinx.api.SpeechAligner;
import edu.cmu.sphinx.result.WordResult;


public class Alphabetizer
{

	/* ===============================================================================================================================
	 * INSTANCE VARIABLES
	 * =============================================================================================================================== */
	
	/**
	 * flag for when I am testing/debugging in order to print information
	 */
	private boolean TESTING = false;
	
	
	/**
	 * the default path to the acoustic model, which is just the standard Endlish one included with CMUSphinx
	 */
	final String DEFAULT_ACOUSTIC_MODEL = "resource:/edu/cmu/sphinx/models/en-us/en-us";
	
	
	/**
	 * the default path to the dictionary, which is just the standard English one included with CMUSphinx
	 */
	final String DEFAULT_DICTIONARY = "resource:/edu/cmu/sphinx/models/en-us/cmudict-en-us.dict";
	
	
	/**
	 * The object that aligns the audio to the transcript
	 */
	private SpeechAligner aligner;
	
	
	/**
	 * the name of the audio file being alphabetized
	 */
	private String fileName;
	
	
	/**
	 * the audio file being alphabetized
	 */
	private File audioFile;
	
	
	/**
	 * the URL to the audio file
	 */
	private URL audioURL;
	
	
	/**
	 * ArrayList containing the words with their timestamps
	 */
	private ArrayList<WordResult> alignedTranscript;
	
	
	/**
	 * the beginning timestamp of every word in alphabetical order, as it will be passed into the python script
	 */
	private String startTimestamps;
	
	
	/**
	 * the ending timestamp of every word in alphabetical order, as it will be passed into the python script
	 */
	private String endTimestamps;
	
	
	/**
	 * the process that runs the python script which sorts the 
	 */
	private Process pythonScript;

	
	/* ===============================================================================================================================
	 * METHODS
	 * =============================================================================================================================== */

	/**
	 * Default Constructor, which uses the default acoustic model 
	 * 
	 * @throws IOException
	 * @throws MalformedURLException
	 */
	public Alphabetizer() throws MalformedURLException, IOException
	{
		aligner = new SpeechAligner(DEFAULT_ACOUSTIC_MODEL, DEFAULT_DICTIONARY, null);
	}
	
	
	
	/**
	 * Constructor for overriding the default configurations of CMUSphinx
	 * 
	 * @param amPath 	path to an acoustic model
	 * @param dictPath	path to a dictionary
	 * @param g2pPath	not exactly sure what this is but it can just be left as null
	 * @throws IOException
	 * @throws MalformedURLException
	 */
	public Alphabetizer(String amPath, String dictPath, String g2pPath) throws MalformedURLException, IOException
	{
		aligner = new SpeechAligner(amPath, dictPath, g2pPath);
	}
	
	
	
	/**
	 * Prepares a given audio file for alphabetization using a transcript
	 * 
	 * In order to make sure for this to work properly, CMUSphinx must get exactly what it
	 * needs. This means:
	 * 
	 * - The audio must be the following format EXACTLY (pasted from the CMUSphinx website):
	 * 		RIFF (little-endian) data, WAVE audio, Microsoft PCM, 16 bit, mono 16000 Hz
	 * 
	 * - The transcript supposedly has to be all lowercase with no punctuation (but I find that
	 * 	some things you can get away with like apostraphes in contractions like "don't")
	 * 
	 * With that said, if you know what you're doing with CMUSphinx then I guess you could
	 * ignore me and configure it however you desire. I'm neither your dad nor CMUSphinx's.
	 * 
	 * @param fileName		the path to the audio file, which MUST be a wav file
	 * @param transcript	the transcript
	 * 
	 * @throws IOException
	 * @throws IllegalArguementException if fileName does not end with ".wav"
	 * @throws InterruptedException
	 */
	@SuppressWarnings("unchecked")
	public void prepareAudio(String file_name, String transcript) throws IOException, InterruptedException
	{
		fileName = file_name;
		
		// throw an IllegalArguementException if the file referenced is not a wav file
		if (!fileName.substring(fileName.length() - 4, fileName.length()).equalsIgnoreCase(".wav"))
			throw new IllegalArgumentException("Audio file must be in WAV format");
		
		
		//initialize the audio file and URL
		audioFile = new File(fileName);
		audioURL = audioFile.toURI().toURL();
		
		
		//allign the transcript
		alignedTranscript = (ArrayList) (aligner.align(audioURL, transcript));
		
		
		//if testing flag is true, print information pertaining to the alignment
		if (TESTING)
		{
			for (WordResult w: alignedTranscript)
			{
				System.out.println(w);
			}
			System.out.println("number of words: " + alignedTranscript.size());
		}
		
		
		//sort the aligned transcript in alphabetical order using insertion sort
		int minIndex;
		
		for (int i = 0; i < alignedTranscript.size() - 1; i++)
		{
			minIndex = i;
			
			for (int j = i; j < alignedTranscript.size(); j++)
			{
				if (alignedTranscript.get(j).getWord().toString().compareTo(alignedTranscript.get(minIndex).getWord().toString()) < 0)
					minIndex = j;
			}
			
			alignedTranscript.add(i, alignedTranscript.remove(minIndex));
		}
		

		//create a string consisting of the beginning and end timestamps of each word in order separated by commas,
		//which will be passed as an argument to the python script
		startTimestamps = "";
		endTimestamps = "";
		
		for (WordResult w: alignedTranscript)
		{
			startTimestamps += w.getTimeFrame().getStart() + ",";
			endTimestamps += w.getTimeFrame().getEnd() + ",";
			
			//some prints for testing too
			if (TESTING)
				System.out.println(w);
		}
		
		
		if (TESTING)
		{
			System.out.println("number of words: " + alignedTranscript.size());
			System.out.println(startTimestamps);
		}
	}
	
	
	
	/**
	 * Alphabetizes the previously prepared audio file such that it cuts as soon as the next word is said
	 * 
	 * Use this ONLY if CMUSphinx picked up every single word, since any missed words will appear out of
	 * place in the alphabetized audio, since they are not accounted for
	 * 
	 * @param outFile 	the name of the output file
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void alphabetize(String outFile) throws IOException, InterruptedException
	{
		//run a Python script to splice audio
		pythonScript = Runtime.getRuntime().exec("python spliceAudio.py " + fileName + " " + outFile + " " + startTimestamps);
		int exitCode = pythonScript.waitFor();
	}
	
	
	
	/**
	 * Alphabetizes the previously prepared audio file such that it cuts at the end of every word
	 * 
	 * This will help produce better results with noisier audio files where not every word is picked up or
	 * if there are large gaps between words, as it just skips them entirely
	 * 
	 * @param outFile 	the name of the output file
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void alphabetizeNoGap(String outFile) throws IOException, InterruptedException
	{		
		//run a Python script to splice audio, but this time with the end timestamps as an extra argument
		pythonScript = Runtime.getRuntime().exec("python spliceAudio.py " + fileName + " " + outFile + " " + startTimestamps + " " + endTimestamps);
		int exitCode = pythonScript.waitFor();
	}

}
