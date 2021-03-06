/*
 * Copyright (c) 2011, Marc Röttig.
 *
 * This file is part of GenericKnimeNodes.
 * 
 * GenericKnimeNodes is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.ballproject.knime.base.node;


import java.util.*;
import java.util.concurrent.*;
import java.io.*;

import java.net.URI;

import org.ballproject.knime.GenericNodesPlugin;
import org.ballproject.knime.base.config.DefaultNodeConfigurationStore;
import org.ballproject.knime.base.config.INodeConfiguration;
import org.ballproject.knime.base.config.CTDNodeConfigurationWriter;
import org.ballproject.knime.base.config.NodeConfigurationStore;
import org.ballproject.knime.base.config.PlainNodeConfigurationWriter;
import org.ballproject.knime.base.external.ExtToolDB;
import org.ballproject.knime.base.external.ExtToolDB.ExternalTool;
import org.ballproject.knime.base.mime.MIMEtype;
import org.ballproject.knime.base.mime.MIMEtypeRegistry;
import org.ballproject.knime.base.parameter.InvalidParameterValueException;
import org.ballproject.knime.base.parameter.Parameter;
import org.ballproject.knime.base.parameter.ListParameter;
import org.ballproject.knime.base.parameter.FileListParameter;
import org.ballproject.knime.base.port.Port;
import org.ballproject.knime.base.preferences.GKNPreferenceInitializer;
import org.ballproject.knime.base.util.ExternalToolRunner;
import org.ballproject.knime.base.util.FileStash;
import org.ballproject.knime.base.util.Helper;
import org.ballproject.knime.base.util.InternalToolRunner;
import org.ballproject.knime.base.util.ToolRunner;
import org.ballproject.knime.base.util.ToolRunner.AsyncToolRunner;
import org.ballproject.knime.base.wrapper.GenericToolWrapper;
import org.eclipse.jface.preference.IPreferenceStore;

import org.knime.core.data.url.MIMEType;
import org.knime.core.data.url.URIContent;
import org.knime.core.data.url.port.MIMEURIPortObject;
import org.knime.core.data.url.port.MIMEURIPortObjectSpec;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;

/**
 * The GenericKnimeNodeModel is the base class for all derived classes within the GenericKnimeNodes system.
 * 
 * The base class is configured using a {@link INodeConfiguration} object, holding information about:
 * <ul>
 *  <li>number of input and output ports</li>
 *  <li> {@link MIMEtype}s of these ports</li>
 * </ul> 
 * 
 * @author
 */
public abstract class GenericKnimeNodeModel extends NodeModel
{
	private static final NodeLogger logger = NodeLogger.getLogger(GenericKnimeNodeModel.class);
	
	protected int[]        selected_output_type;
	protected String       binpath;
	protected String       pluginname; 
	public String output="";
	
	/*
	 * stores the node configuration (i.e. parameters, ports, ..) 
	 */
	protected INodeConfiguration config;
	
	/*
	 * stores general properties 
	 */
	protected Properties props;
	
	/*
	 * stores environment variables needed for program execution 
	 */
	protected Map<String,String> env;
	
	/**
	 * Constructor for the node model.
	 */
	protected GenericKnimeNodeModel(INodeConfiguration config)
	{
		super(createOPOs(config.getInputPorts()),createOPOs(config.getOutputPorts()));
		this.config = config;
		init();
	}
	
	protected void init()
	{
		// init with [0,0,....,0]
		selected_output_type = new int[this.config.getNumberOfOutputPorts()];
	}

	protected MIMEtype getOutputType(int idx)
	{
		return this.config.getOutputPorts()[idx].getMimeTypes().get(selected_output_type[idx]);
	}
	
	protected int getOutputTypeIndex(int idx)
	{
		return selected_output_type[idx];
	}
	
	public static final PortType OPTIONAL_PORT_TYPE = new PortType(MIMEURIPortObject.class, true);

	private static PortType[] createOPOs(Port[] ports)
	{
		PortType[] portTypes = new PortType[ports.length];
	    Arrays.fill(portTypes, MIMEURIPortObject.TYPE);
	    for(int i=0;i<ports.length;i++)
	    {
	    	if(ports[i].isOptional())
	    	{
	    		portTypes[i] = OPTIONAL_PORT_TYPE;
	    	}
	    }
	    return portTypes;
	}
	
	
	protected NodeConfigurationStore store = new DefaultNodeConfigurationStore();
	
	protected ToolRunner tr;
	
	private void prepareExecute(final File jobdir, final ExecutionContext exec) throws Exception
	{
		String nodeName = config.getName();

		if(config.getStatus().equals("internal"))
		{
			InternalToolRunner tr_ = new InternalToolRunner();
			
			// fill params.xml
			CTDNodeConfigurationWriter writer = new CTDNodeConfigurationWriter(config.getXML());
			writer.init(store);
			
			if(this.props.getProperty("use_ini").equals("true"))
			{
				tr_.setParamSwitch("-ini");
				writer.writeINI(jobdir+FILESEP+"params.xml");
			}
			else
			{
				tr_.setParamSwitch("-par");
				writer.write(jobdir+FILESEP+"params.xml");
			}
			
			
			// get executable name
			String exepath = Helper.getExecutableName(nodeName, binpath+FILESEP+"bin");
			
			if(exepath==null)
			{
				throw new Exception("execution of internal tool failed: due to missing executable file");
			}
			
			tr_.setExecutablePath(exepath);
			
			tr = tr_;
		}
		else
		{
			ExternalToolRunner tr_ = new ExternalToolRunner();
			
			PlainNodeConfigurationWriter writer = new PlainNodeConfigurationWriter();
			store.setParameterValue("jobdir", jobdir.getAbsolutePath());
			writer.init(store);
			writer.write(jobdir+File.separator+"params.ini");
			
			GenericToolWrapper wrapper = new GenericToolWrapper(config, store);
			List<String> switches = wrapper.getSwitchesList();
			tr_.setSwitches(switches);
			
			String exepath = ExtToolDB.getInstance().getToolPath(new ExternalTool(pluginname, nodeName));
			
			if(exepath==null||!(new File(exepath).exists()))
			{
				throw new Exception("execution of external tool failed: due to missing executable file '"+exepath+"'");
			}
			
			tr_.setExecutablePath(exepath);
			
			tr = tr_;
		}
		
		executeTool(jobdir, exec);
	}
		
	private static String PATHSEP = System.getProperty("path.separator");
	
	private void executeTool(final File jobdir, final ExecutionContext exec) throws Exception
	{
		
		AsyncToolRunner     t      = new AsyncToolRunner(tr);
		t.getToolRunner().setJobDir(jobdir.getAbsolutePath());
		
		IPreferenceStore store = GenericNodesPlugin.getDefault().getPreferenceStore();
		for(String key: env.keySet())
		{
			String value = "";
			
			String addPathes = store.getString(GKNPreferenceInitializer.PREF_PATHES);
			value = env.get(key).replace("$ROOT", binpath+FILESEP);
			if(key.equals("PATH"))
			{
				if(!addPathes.equals(""))
					value += PATHSEP+addPathes;
				value += PATHSEP+System.getenv("PATH");
			}
			
			t.getToolRunner().addEnvironmentEntry(key, value);
			GenericNodesPlugin.log(key+"->"+value);
		}
		
		FutureTask<Integer> future = new FutureTask<Integer>(t);
			
		ExecutorService     executor = Executors.newFixedThreadPool(1);
		executor.execute(future);
		
		while (!future.isDone())
        {
            try
            {
                Thread.sleep(5000);
            } 
            catch (InterruptedException ie)
            {
            }
            
            try
            {
            	exec.checkCanceled();	
            }
            catch(CanceledExecutionException e)
            {
            	t.kill();
            	executor.shutdown();
            	throw e;
            }
        }
		
		int retcode = -1;
        try
        {
        	retcode = future.get();
        } 
        catch (ExecutionException ex)
        {
        	ex.printStackTrace();
        }

        executor.shutdown();
				
		output = t.getToolRunner().getOutput();
		
		GenericNodesPlugin.log(output);
		GenericNodesPlugin.log("retcode="+retcode);
		
		if(retcode!=0)
	    {
	    	logger.error(output);
	    	throw new Exception("execution of external tool failed");
	    }
		
	}
	
	protected MIMEtypeRegistry resolver = GenericNodesPlugin.getMIMEtypeRegistry();
	private static String FILESEP = File.separator;
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void reset()
	{
		// TODO Code executed on reset.
		// Models build during execute are cleared here.
		// Also data handled in load/saveInternals will be erased here.
		/*
		for(Parameter<?> param: config.getParameters())
		{
			param.setValue(null);
		}
		*/
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void saveSettingsTo(final NodeSettingsWO settings)
	{
		for(String key: this.config.getParameterKeys())
		{
			settings.addString(key, this.config.getParameter(key).getStringRep());
		}
		for(int i=0;i<this.config.getNumberOfOutputPorts();i++)
		{
			settings.addInt("GENERIC_KNIME_NODES_outtype#"+i,this.getOutputTypeIndex(i));
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException
	{
		// - we know that values are validated and thus are valid
		// - we xfer the values into the corresponding model objects
		for(String key: this.config.getParameterKeys())
		{
			String value = settings.getString(key);
			try
			{
				this.config.getParameter(key).fillFromString(value);
			}
			catch (InvalidParameterValueException e)
			{
				e.printStackTrace();
			}
		}
		
		for(int i=0;i<this.config.getNumberOfOutputPorts();i++)
		{
			int idx = settings.getInt("GENERIC_KNIME_NODES_outtype#"+i);
			this.selected_output_type[i] = idx; 
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException
	{
		// - we validate incoming settings values here
		// - we do not xfer values to member variables
		// - we throw an exception if something is invalid
		
		for(String key: this.config.getParameterKeys())
		{
			Parameter<?> param = config.getParameter(key);
			if(!param.getIsOptional())
			{
				if(!settings.containsKey(key))
				{
					GenericNodesPlugin.log("\t no key found for mand. parameter "+key);
					throw new InvalidSettingsException("no value for mandatory parameter "+key+" supplied");
				}
				if(settings.getString(key)==null)
				{
					GenericNodesPlugin.log("\t null value found for mand. parameter "+key);
					throw new InvalidSettingsException("no value for mandatory parameter "+key+" supplied");
				}
			}
			
			String value = settings.getString(key);
			try
			{
				param.fillFromString(value);
			}
			catch (InvalidParameterValueException e)
			{
				GenericNodesPlugin.log("\t invalid value for parameter "+key);
				throw new InvalidSettingsException("invalid value for parameter "+key);
			}
		}		
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadInternals(final File internDir, final ExecutionMonitor exec) throws IOException, CanceledExecutionException
	{
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void saveInternals(final File internDir, final ExecutionMonitor exec) throws IOException, CanceledExecutionException
	{
	}

	protected MIMEType[][] mimetypes_in;
	protected MIMEType[][] mimetypes_out;
	protected PortObjectSpec[] outspec_;
	
	@Override
	protected PortObjectSpec[] configure(PortObjectSpec[] inSpecs) throws InvalidSettingsException
	{
		for(Parameter<?> param: this.config.getParameters())
		{
			//System.out.println(param.getKey()+" "+param.getIsOptional()+" "+param.isNull()+" |"+param.getStringRep());
			if(!param.getIsOptional()&&param.getStringRep().equals(""))
			{
				//throw new InvalidSettingsException("not all mandatory parameters are set");
				this.setWarningMessage("some mandatory parameters might not be set");
			}
			
		}
		
		int  nIn = mimetypes_in.length;
		
		for(int i=0;i<nIn;i++)
		{
			// not connected input ports have nulls in inSpec
			if (inSpecs[i] == null)
			{
				// .. if port is optional everything is fine
				if (config.getInputPorts()[i].isOptional())
				{
					continue;
				} 
				else
					throw new InvalidSettingsException("non-optional input port not connected");
			}
			

			MIMEURIPortObjectSpec spec = (MIMEURIPortObjectSpec) inSpecs[i];
			
			// get input MIMEType
			MIMEType mt = spec.getMIMEType(); 

			// check whether input MIMEType is in list of allowed MIMETypes
			boolean ok = false;
			String mismatch="";
			
			for(int j=0;j<mimetypes_in[i].length;j++)
			{
				if(mt.equals(mimetypes_in[i][j]))
				{
					ok = true;
				}
				else
				{
					mismatch = String.format("in: [%s] expected:[%s]",mt,Arrays.toString(mimetypes_in[i]));
				}
			}
			if(!ok)
				throw new InvalidSettingsException("invalid MIMEtype at port number "+i+" : "+mismatch);
		}
		
		// create output spec
		outspec_ = createOutSpec();
		
		return outspec_;
	}
	
	protected PortObjectSpec[] createOutSpec()
	{
		int nOut = mimetypes_out.length;
		PortObjectSpec[]  out_spec   = new PortObjectSpec[nOut];

		// set selected MIMEURIPortObjectSpecs at output ports
		for(int i=0;i<nOut;i++)
		{
			// selected output MIMEType 
			int selectedMIMETypeIndex = getOutputTypeIndex(i);
			out_spec[i] = new MIMEURIPortObjectSpec(mimetypes_out[i][selectedMIMETypeIndex]);
		}
		
		return out_spec;
	}

	@Override
	protected PortObject[] execute(PortObject[] inObjects, ExecutionContext exec) throws Exception
	{
		// fetch node descriptors
		String nodeName = config.getName();

		// create job directory
		File jobdir = new File(Helper.getTemporaryDirectory(nodeName,!GenericNodesPlugin.isDebug()));
		GenericNodesPlugin.log("jobdir=" + jobdir);

		store = new DefaultNodeConfigurationStore();

		// prepare input data and parameter values
		List<List<URI>> output_files = outputParameters(jobdir, inObjects);

		// launch executable
		prepareExecute(jobdir, exec);

		// process result files
		PortObject[] outports = processOutput(output_files, exec);

		if (!GenericNodesPlugin.isDebug())
			Helper.deleteDirectory(jobdir);

		return outports;
	}
	
	private List<List<URI>> outputParameters(File jobdir, PortObject[] inData) throws Exception
	{	
		// .. input files
		for(int i=0;i<inData.length;i++)
		{
			// skip optional and unconnected inport ports
			if(inData[i]==null)
				continue;
			
			Port port = config.getInputPorts()[i];
			
			MIMEURIPortObject po = (MIMEURIPortObject) inData[i];
			List<URIContent> uris = po.getURIContents();
			
			String   name        = port.getName();
			boolean  isMultiFile = port.isMultiFile();
						
			if(uris.size()>1 && !isMultiFile)
			{
				throw new Exception("MIMEURIPortObject with multiple URIs supplied at single URI port #"+i);
			}
			
			for(URIContent uric : uris)
			{
				URI uri = uric.getURI();
				String filename = new File(uri).getAbsolutePath();
				GenericNodesPlugin.log("< setting param "+name+"->"+filename);
				store.setParameterValue(name, filename);
			}
		}
		
		List<List<URI>> outfiles = new ArrayList<List<URI>>();

		Map<Port, Integer> port2slot = new HashMap<Port, Integer>();

		// .. output files
		int nOut = config.getOutputPorts().length;
		for (int i = 0; i < nOut; i++)
		{
			Port   port = config.getOutputPorts()[i];
			String name = port.getName();

			String ext = this.getOutputType(i).getExt();

			if (port.isMultiFile())
			{
				// keep this list empty for now ...
				List<URI> files = new ArrayList<URI>();
				outfiles.add(files);
				// but store the slot index for later filling
				port2slot.put(port, i);
			} 
			else
			{
				List<URI> files = new ArrayList<URI>();
				String filename = FileStash.getInstance().allocateFile(ext);
				GenericNodesPlugin.log("> setting param " + name + "->" + filename);
				store.setParameterValue(name, filename);
				files.add(new File(filename).toURI());
				outfiles.add(files);
			}
		}

		// .. node parameters
		for (String key : config.getParameterKeys())
		{
			Parameter<?> param = config.getParameter(key);
			if (param.isNull())
			{
				if (param.getIsOptional())
					continue;
			}
			if (param instanceof ListParameter)
			{
				ListParameter lp = (ListParameter) param;
				if (param instanceof FileListParameter)
				{
					// FIXME
					
					FileListParameter flp = (FileListParameter) param;
					List<String> files = lp.getStrings();

					int slot = port2slot.get(flp.getPort());

					String ext = this.getOutputType(slot).getExt();

					for (String file : files)
					{
						String filename = FileStash.getInstance().allocateFile(ext);
						//URL fileurl = FileStash.getInstance().allocatePortableFile(ext);
						//String filename = fileurl.openConnection().getURL().getFile();
						//String filename = jobdir.getAbsolutePath() + File.separator + file + "." + ext;
						outfiles.get(slot).add(new File(filename).toURI());
						store.setMultiParameterValue(key, filename);
					}
					
				} 
				else
				{
					for (String val : lp.getStrings())
					{
						GenericNodesPlugin.log("@@ setting param " + key + "->"+ val);
						store.setMultiParameterValue(key, val);
					}
				}
			} else
			{
				GenericNodesPlugin.log("@ setting param " + key + "->"+ param.getValue().toString());
				store.setParameterValue(key, param.getValue().toString());
			}
		}

		return outfiles;
	}

	private PortObject[] processOutput(List<List<URI>> my_outnames, ExecutionContext exec) throws Exception
	{
		int nOut = config.getOutputPorts().length;
		
        // create output tables
		MIMEURIPortObject[] outports = new MIMEURIPortObject[nOut];
		
        for(int i=0;i<nOut;i++)
        {        	
        	List<URIContent> uris = new ArrayList<URIContent>();
        	
        	String some_filename="";
        	// multi output file
        	for(URI filename: my_outnames.get(i))
        	{
        		some_filename = filename.getPath();
        		uris.add( new URIContent(filename) );
        	}
        	
    		outports[i] = new MIMEURIPortObject(uris, resolveMIMEType(some_filename));
        }
        
        return outports;
	}
	
	private MIMEType resolveMIMEType(String filename)
	{
		return resolver.getMIMEtype(filename);
	}
	
	
}
