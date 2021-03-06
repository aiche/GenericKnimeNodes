package org.ballproject.knime.nodegeneration.templates;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.ballproject.knime.nodegeneration.NodeGenerator;
import org.ballproject.knime.nodegeneration.model.mime.MimeType;

public class MimeFileCellFactoryTemplate extends Template {

	private static final Logger LOGGER = Logger
			.getLogger(BinaryResourcesTemplate.class.getCanonicalName());

	public MimeFileCellFactoryTemplate(String packageName,
			List<MimeType> mimeTypes) throws IOException {
		super(NodeGenerator.class
				.getResourceAsStream("templates/MimeFileCellFactory.template"));

		String mimeTypeAddTemplateCodeLine = "\t\tmimetypes.add(new MIMEType(\"__EXT__\"));\n";
		String mimeTypeAddCode = "";

		Set<MimeType> processedMimeTypes = new HashSet<MimeType>();

		for (MimeType mimeType : mimeTypes) {
			LOGGER.info("MIME Type read: " + mimeType.getName());

			if (processedMimeTypes.contains(mimeType)) {
				LOGGER.log(Level.WARNING, "skipping duplicate mime type "
						+ mimeType.getName());
			} else {
				processedMimeTypes.add(mimeType);
			}

			mimeTypeAddCode += mimeTypeAddTemplateCodeLine.replace("__EXT__",
					mimeType.getExt().toLowerCase());
		}

		this.replace("__MIMETYPES__", mimeTypeAddCode);
		this.replace("__BASE__", packageName);
	}
}
