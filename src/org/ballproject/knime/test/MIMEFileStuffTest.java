package org.ballproject.knime.test;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

import org.ballproject.knime.base.util.Helper;
import org.ballproject.knime.test.data.TestDataSource;
import org.ballproject.knime.test.mocks.MockMIMEFileCell;
import org.junit.Test;

public class MIMEFileStuffTest
{

	@Test
	public void test1() throws IOException
	{
		String       tmpfile1 = Helper.getTemporaryFilename("unk", true);
		Helper.copyStream(TestDataSource.class.getResourceAsStream("test.unk"), new File(tmpfile1));
		
		String       tmpfile2 = Helper.getTemporaryFilename("ctd", true);
		Helper.copyStream(TestDataSource.class.getResourceAsStream("test2.ctd"), new File(tmpfile2));
		
		MockMIMEFileCell mfc1     = new MockMIMEFileCell();
		mfc1.read(new File(tmpfile1));
		assertEquals(mfc1.toString(),"UNKMimeFileCell");
		
		MockMIMEFileCell mfc2     = new MockMIMEFileCell();
		mfc2.read(new File(tmpfile1));
		
		assertTrue(mfc1.equals(mfc2));
		
		MockMIMEFileCell mfc3     = new MockMIMEFileCell();
		mfc3.read(new File(tmpfile2));
		
		
		assertEquals(mfc1.getExtension(),"unk");
		
		assertFalse(mfc1.equals(mfc3));
		assertFalse(mfc2.equals(mfc3));
		
		assertEquals(mfc1.hashCode(),mfc2.hashCode());
		assertTrue(mfc1.hashCode()!=mfc3.hashCode());
		assertTrue(mfc2.hashCode()!=mfc3.hashCode());
		
	}

}
