package org.sagebionetworks.web.unitserver;

import static org.junit.Assert.assertTrue;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

public class TestUtils {
	
	@SuppressWarnings("rawtypes")
	public static BaseMatcher createByteArrayPrefixMatcher(final byte[] expected) {
		BaseMatcher matcher=new BaseMatcher(){
			@Override
			public void describeTo(Description description) {
				// nothing
			}
			// check that first character is A
			@Override
			public boolean matches(Object item) {
				byte[] actual=(byte[]) item;				
				assertTrue(actual.length >= expected.length); // buffer must be larger
				// check that expected prefixes actual. 
				int i=0;
				while(i<expected.length) {
					if(expected[i] != actual[i]) return false;
					i++;
				}
				// the rest of the buffer should be 0s
				while(i<actual.length) {
					if(actual[i] != 0) return false;
					i++;
				}
				return true;
			}
		};
		return matcher;
	}

}
