package io.github.givimad.whisperjni;

public class TokenData {
	
	// We have to add this ourselves in the cpp side cause its not part of the struct for which this is a wrapper for
	public final String token;
	
	public final int id; // token id
	public final int tid; // forced timestamp token id
	
	public final float p; // probability of the token
	public final float plog; // log probability of the token
	public final float pt; // probability of the timestamp token
	public final float ptsum; // sum of probabilities of all timestamp tokens
	
	// token-level timestamp data
	// do not use if you haven't computed token-level timestamps
	public final long t0; // start time of the token
	public final long t1; // end time of the token
	
	// [EXPERIMENTAL] Token-level timestamps with DTW
	// do not use if you haven't computed token-level timestamps with dtw
	// Roughly corresponds to the moment in audio in which the token was output
	public final long t_dtw;
	
	public final float vlen; // voice length of the token
	
	/**
	 * Internal context constructor
	 * 
	 * @param whisper library instance
	 * @param ref     native pointer identifier
	 */
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
