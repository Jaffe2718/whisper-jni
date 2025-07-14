package io.github.freshsupasulley.whisperjni;

public class WhisperVADSegment {
	
	public final float startSeconds;
	public final float endSeconds;
	
	public WhisperVADSegment(float start, float end)
	{
		this.startSeconds = start;
		this.endSeconds = end;
	}
}
