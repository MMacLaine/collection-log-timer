package com.clogtimer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SortMode
{
	COMPLETION_PCT_DESC("Most Common First"),
	COMPLETION_PCT_ASC("Rarest First"),
	EHC_ASC("Fastest EHC First"),
	EHC_DESC("Slowest EHC First"),
	ALPHABETICAL("Alphabetical");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
