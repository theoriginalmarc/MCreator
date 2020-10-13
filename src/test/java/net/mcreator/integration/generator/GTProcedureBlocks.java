/*
 * MCreator (https://mcreator.net/)
 * Copyright (C) 2020 Pylo and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.mcreator.integration.generator;

import net.mcreator.blockly.IBlockGenerator;
import net.mcreator.blockly.data.BlocklyLoader;
import net.mcreator.blockly.data.StatementInput;
import net.mcreator.blockly.data.ToolboxBlock;
import net.mcreator.element.ModElementType;
import net.mcreator.element.types.Procedure;
import net.mcreator.generator.GeneratorStats;
import net.mcreator.minecraft.ElementUtil;
import net.mcreator.util.ListUtils;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import org.apache.logging.log4j.Logger;

import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GTProcedureBlocks {

	public static void runTest(Logger LOG, String generatorName, Random random, Workspace workspace) {
		if (workspace.getGenerator().getGeneratorStats().getModElementTypeCoverageInfo().get(ModElementType.PROCEDURE)
				== GeneratorStats.CoverageStatus.NONE) {
			LOG.warn("[" + generatorName
					+ "] Skipping procedure blocks test as the current generator does not support them.");
			return;
		}

		Set<String> generatorBlocks = workspace.getGenerator().getGeneratorStats().getGeneratorProcedures();

		for (ToolboxBlock procedureBlock : BlocklyLoader.INSTANCE.getProcedureBlockLoader().getDefinedBlocks()
				.values()) {

			StringBuilder additionalXML = new StringBuilder();

			if (!generatorBlocks.contains(procedureBlock.machine_name)) {
				LOG.warn("[" + generatorName + "] Skipping procedure block that is not defined by generator: "
						+ procedureBlock.machine_name);
				continue;
			}

			if (procedureBlock.toolboxXML == null) {
				LOG.warn("[" + generatorName + "] Skipping procedure block without default XML defined: "
						+ procedureBlock.machine_name);
				continue;
			}

			if (procedureBlock.inputs != null) {
				boolean templatesDefined = true;

				if (procedureBlock.toolbox_init != null) {
					for (String input : procedureBlock.inputs) {
						boolean match = false;
						for (String toolboxtemplate : procedureBlock.toolbox_init) {
							if (toolboxtemplate.contains("<value name=\"" + input + "\">")) {
								match = true;
								break;
							}
						}

						if (!match) {
							templatesDefined = false;
							break;
						}
					}
				} else {
					templatesDefined = false;
				}

				if (!templatesDefined) {
					LOG.warn("[" + generatorName + "] Skipping procedure block with incomplete template (no test atm): "
							+ procedureBlock.machine_name);
					continue;
				}
			}

			if (procedureBlock.required_apis != null) {
				boolean skip = false;

				for (String required_api : procedureBlock.required_apis) {
					if (!workspace.getWorkspaceSettings().getMCreatorDependencies().contains(required_api)) {
						skip = true;
						break;
					}
				}

				if (skip) {
					LOG.warn("[" + generatorName + "] Skipping API specific procedure block: "
							+ procedureBlock.machine_name);
					continue;
				}
			}

			if (procedureBlock.fields != null) {
				LOG.warn("[" + generatorName + "] Skipping procedure block with fields (no test atm): "
						+ procedureBlock.machine_name);
				continue;
			}

			if (procedureBlock.statements != null) {
				for (StatementInput statement : procedureBlock.statements) {
					additionalXML.append("<statement name=\"").append(statement.name).append("\">")
							.append("<block type=\"text_print\"><value name=\"TEXT\"><block type=\"math_number\">"
									+ "<field name=\"NUM\">123.456</field></block></value></block>")
							.append("</statement>\n");
				}
			}

			ModElement modElement = new ModElement(workspace, "TestBlock" + procedureBlock.machine_name,
					ModElementType.PROCEDURE);

			String testXML = procedureBlock.toolboxXML;

			// set MCItem block to some value
			testXML = testXML.replace("<block type=\"mcitem_allblocks\"><field name=\"value\"></field></block>",
					"<block type=\"mcitem_allblocks\"><field name=\"value\">" + ListUtils
							.getRandomItem(random, ElementUtil.loadBlocks(modElement.getWorkspace())).getName()
							+ "</field></block>");

			testXML = testXML.replace("<block type=\"mcitem_all\"><field name=\"value\"></field></block>",
					"<block type=\"mcitem_all\"><field name=\"value\">" + ListUtils
							.getRandomItem(random, ElementUtil.loadBlocksAndItems(modElement.getWorkspace())).getName()
							+ "</field></block>");

			// replace all math blocks with blocks that contain double value to verify type casting
			testXML = testXML.replace("<block type=\"coord_x\"></block>",
					"<block type=\"variables_get_number\"><field name=\"VAR\">local:test</field></block>");
			testXML = testXML.replace("<block type=\"coord_y\"></block>",
					"<block type=\"variables_get_number\"><field name=\"VAR\">local:test</field></block>");
			testXML = testXML.replace("<block type=\"coord_z\"></block>",
					"<block type=\"variables_get_number\"><field name=\"VAR\">local:test</field></block>");
			testXML = testXML.replaceAll("<block type=\"math_number\"><field name=\"NUM\">(.*?)</field></block>",
					"<block type=\"variables_get_number\"><field name=\"VAR\">local:test</field></block>");

			testXML = testXML.replace("<block type=\"" + procedureBlock.machine_name + "\">",
					"<block type=\"" + procedureBlock.machine_name + "\">" + additionalXML.toString());

			Procedure procedure = new Procedure(modElement);

			if (procedureBlock.type == IBlockGenerator.BlockType.PROCEDURAL) {
				procedure.procedurexml = wrapWithBaseTestXML(testXML);
			} else { // output block type
				String rettype = procedureBlock.getOutputType();
				switch (rettype) {
				case "Number":
					procedure.procedurexml = wrapWithBaseTestXML(
							"<block type=\"return_number\"><value name=\"return\">" + testXML + "</value></block>");
					break;
				case "Boolean":
					procedure.procedurexml = wrapWithBaseTestXML(
							"<block type=\"return_logic\"><value name=\"return\">" + testXML + "</value></block>");

					break;
				case "String":
					procedure.procedurexml = wrapWithBaseTestXML(
							"<block type=\"return_text\"><value name=\"return\">" + testXML + "</value></block>");
					break;
				case "MCItem":
					procedure.procedurexml = wrapWithBaseTestXML(
							"<block type=\"return_itemstack\"><value name=\"return\">" + testXML + "</value></block>");
					break;
				default:
					procedure.procedurexml = wrapWithBaseTestXML(
							"<block type=\"text_print\"><value name=\"TEXT\">" + testXML + "</value></block>");
					break;
				}
			}

			try {
				workspace.addModElement(modElement);
				assertTrue(workspace.getGenerator().generateElement(procedure));
				workspace.getModElementManager().storeModElement(procedure);
			} catch (Throwable t) {
				fail("[" + generatorName + "] Failed generating procedure block: " + procedureBlock.machine_name);
				t.printStackTrace();
			}
		}

	}

	private static String wrapWithBaseTestXML(String customXML) {
		return "<xml xmlns=\"https://developers.google.com/blockly/xml\">"
				+ "<variables><variable type=\"Number\" id=\"test\">test</variable></variables>"
				+ "<block type=\"event_trigger\" deletable=\"false\" x=\"73\" y=\"64\"><field name=\"trigger\">no_ext_trigger</field><next>"
				+ "<block type=\"variables_set_number\"><field name=\"VAR\">local:test</field><value name=\"VAL\">"
				+ "<block type=\"math_dual_ops\"><field name=\"OP\">ADD</field><value name=\"A\">"
				+ "<block type=\"variables_get_number\"><field name=\"VAR\">local:test</field></block></value>"
				+ "<value name=\"B\"><block type=\"math_number\"><field name=\"NUM\">1.2</field></block></value></block></value>"
				+ "<next>" + customXML + "</next></block></next></block></xml>";
	}

}
