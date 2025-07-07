package io.github.givimad.whisperjni;

/**
 * Represents the data of an individual token.
 * 
 * @author Sullbeans
 */
public class TokenData {
	
	// We have to add this ourselves in the cpp side cause its not part of the struct for which this is a wrapper for
	public final String token;
	
	/** Token ID */
	public final int id;
	/** Forced timestamp token ID */
	public final int tid;
	
	/** Probability [0.0 - 1.0] */
	public final float p;
	/** Log probability of the token */
	public final float plog;
	/** Probability of the timestamp token */
	public final float pt;
	/** Sum of probabilities of all timestamp tokens */
	public final float ptsum;
	
	// token-level timestamp data
	// do not use if you haven't computed token-level timestamps
	/** Start time of the token */
	public final long t0;
	/** End time of the token */
	public final long t1; // end time of the token
	
	// [EXPERIMENTAL] Token-level timestamps with DTW
	// do not use if you haven't computed token-level timestamps with dtw
	/** Roughly corresponds to the moment in audio in which the token was output */
	public final long t_dtw;
	/** Voice length of the token */
	public final float vlen;
	
	protected TokenData(String token, int id, int tid, float p, float plog, float pt, float ptsum, long t0, long t1, long t_dtw, float vlen)
	{
		this.token = token;
		this.id = id;
		this.tid = tid;
		this.p = p;
		this.plog = plog;
		this.pt = pt;
		this.ptsum = ptsum;
		this.t0 = t0;
		this.t1 = t1;
		this.t_dtw = t_dtw;
		this.vlen = vlen;
	}
	
	@Override
	public String toString()
	{
		return super.toString() + " -- " + token + ", probability: " + p;
	}
}
