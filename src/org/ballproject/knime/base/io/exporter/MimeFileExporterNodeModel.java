package org.ballproject.knime.base.io.exporter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.ballproject.knime.base.mime.MIMEFileCell;
import org.ballproject.knime.base.port.*;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;


/**
 * This is the model implementation of MimeFileImporter.
 * 
 * 
 * @author roettig
 */
public class MimeFileExporterNodeModel extends NodeModel
{

	// the logger instance
	private static final NodeLogger logger = NodeLogger.getLogger(MimeFileExporterNodeModel.class);

	static final String CFG_FILENAME = "FILENAME";

	private SettingsModelString  m_filename = MimeFileExporterNodeDialog.createFileChooserModel();
	

	/**
	 * Constructor for the node model.
	 */
	protected MimeFileExporterNodeModel()
	{
		super(1, 0);
	}
	
	public byte[] data = new byte[]{};
	protected MIMEFileCell cell_;
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	protected BufferedDataTable[] execute(final BufferedDataTable[] inData, final ExecutionContext exec) throws Exception
	{			
		DataRow  row  = inData[0].iterator().next();
		DataCell cell = row.getCell(0);
		if( cell instanceof MimeMarker)
		{
			cell_ = (MIMEFileCell) cell;
			
			MimeMarker mrk = (MimeMarker) cell;
			MIMEFileDelegate del = mrk.getDelegate();
		
			del.write(m_filename.getStringValue());
			data = del.getByteArrayReference();
		}
		return new BufferedDataTable[]{};
	}


	
	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void reset()
	{
		// TODO Code executed on reset.
		// Models build during execute are cleared here.
		// Also data handled in load/saveInternals will be erased here.
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	protected DataTableSpec[] configure(final DataTableSpec[] inSpecs) throws InvalidSettingsException
	{		
		return new DataTableSpec[]{};
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void saveSettingsTo(final NodeSettingsWO settings)
	{
		m_filename.saveSettingsTo(settings);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException
	{
		m_filename.loadSettingsFrom(settings);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException
	{
		m_filename.validateSettings(settings);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadInternals(final File internDir, final ExecutionMonitor exec) throws IOException, CanceledExecutionException
	{
		ZipFile zip = new ZipFile(new File(internDir,"loadeddata"));
		
		@SuppressWarnings("unchecked")
		Enumeration<ZipEntry> entries = (Enumeration<ZipEntry>) zip.entries();

		int    BUFFSIZE = 2048;
		byte[] BUFFER   = new byte[BUFFSIZE];
		
	    while(entries.hasMoreElements()) 
	    {
	        ZipEntry entry = (ZipEntry)entries.nextElement();
	        if(entry.getName().equals("rawdata.bin"))
	        {
	        	int  size = (int) entry.getSize(); 
	        	data = new byte[size];
	        	InputStream in = zip.getInputStream(entry);
	        	int len;
	        	int totlen=0;
	        	while( (len=in.read(BUFFER, 0, BUFFSIZE))>=0 )
	        	{
	        		System.arraycopy(BUFFER, 0, data, totlen, len);
	        		totlen+=len;
	        	}
	        }
	    }
	    zip.close();
	
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void saveInternals(final File internDir, final ExecutionMonitor exec) throws IOException, CanceledExecutionException
	{
		ZipOutputStream out = new ZipOutputStream(new FileOutputStream(new File(internDir,"loadeddata")));
		ZipEntry entry = new ZipEntry("rawdata.bin");
	    out.putNextEntry(entry);
	    out.write(data);
	    out.close(); 
	
	}
	
	
}