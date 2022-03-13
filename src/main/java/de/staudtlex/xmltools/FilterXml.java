/*
 * Copyright (C) 2022 Alexander Staudt
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package de.staudtlex.xmltools;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;


/**
 * FilterXml provides a method to filter XML files and return those files that
 * contain nodes matching a user-defined XPath.
 */
public class FilterXml {
  /**
   * Returns the nodes of an XML file matching a given XPath expression
   * 
   * @param xmlFile XML file from which to extract nodes
   * @param expr    compiled XPath expression selecting desired nodes
   * @return a list of nodes matching the XPath
   */
  public static NodeList findInXml(final File xmlFile,
      final XPathExpression expr) {
    try {
      final DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory
          .newInstance();
      docBuilderFactory.setNamespaceAware(true);
      final DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
      final Document doc = docBuilder.parse(xmlFile);
      doc.getDocumentElement().normalize();
      return (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
    } catch (final Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * Implements the NamespaceContext-interface. This implementation differs from
   * the default NamespaceContext in that it dynamically constructs a context
   * from a XML namespace declaration string of the form 'xmlns:prefix="URI"'.
   */
  public static class FilterXmlNamespaceContext implements NamespaceContext {
    private final String namespacePrefix;
    private final String namespace;

    /**
     * Creates a FilterXmlNamespaceContext instance from a given XML namespace
     * string.
     * 
     * @param xmlns XML namespace string of the form 'xmlns:prefix="URI"'
     */
    public FilterXmlNamespaceContext(final String xmlns) {
      if (!xmlns.equals("")) {
        this.namespacePrefix = xmlns.split(":", 2)[1].split("=", 2)[0];
        this.namespace = xmlns.split(":", 2)[1].split("=", 2)[1];
      } else {
        this.namespacePrefix = "";
        this.namespace = "";
      }
    }

    @Override
    public String getNamespaceURI(final String prefix) {
      if (prefix.equals(namespacePrefix)) {
        return namespace;
      }
      return XMLConstants.NULL_NS_URI;
    }

    @Override
    public String getPrefix(final String namespaceURI) {
      return null;
    }

    @Override
    public Iterator<String> getPrefixes(final String namespaceURI) {
      return null;
    }
  }

  /**
   * Filters XML files that contain nodes matching a given XPath expression and
   * copies the matching files to a user-specified directory.
   * 
   * @param args (1) the path to the directory containing the XML files to be
   *               filtered, (2) the destination directory for the files whose
   *               nodes match the XPath expression, (3) the XPath expression to
   *               be matched, (4) an (optional) XML namespace declaration
   */
  public static void main(String[] args) {
    if (args.length < 3) {
      System.err.println("FilterXml requires at least 3 arguments.");
      System.exit(1);
    }

    // Get filepaths and list of relevant XML-files
    final Path inDir = Paths.get(args[0]);
    final Path outDir = Paths.get(args[1]);
    final String query = args[2];

    // Get namespace
    String xmlns = "";
    if (args.length == 4) {
      xmlns = args[3];
    }

    // Check if directories are actual directories
    if (!Files.isDirectory(inDir)) {
      System.err.println(inDir.toString() + " is not a directory.");
      System.exit(1);
    }
    if (!Files.isDirectory(outDir)) {
      System.err.println(outDir.toString() + " is not a directory.");
      System.exit(1);
    }

    // Get list of XML files
    List<Path> fileList = new ArrayList<Path>(0);
    try {
      fileList = Files.list(inDir)
          .filter(f -> Files.isRegularFile(f) && !f.toFile().isHidden())
          .filter(f -> f.getFileName().toString().endsWith("xml"))
          .collect(Collectors.toList());
    } catch (final IOException e) {
      // e.printStackTrace();
      System.err.println("An error occurred while opening "
          + inDir.getFileName().toString() + ".");
      System.exit(1);
    }
    if (fileList.size() < 1) {
      System.err.println(inDir.getFileName().toString() + " is empty.");
      System.exit(1);
    }

    try {
      // Compile XPath query to XPath expression
      final XPath xpath = XPathFactory.newInstance().newXPath();
      xpath.setNamespaceContext(new FilterXmlNamespaceContext(xmlns));
      final XPathExpression expr = xpath.compile(query);

      // Retrieve XPath expression result nodes from each XML file
      final LinkedHashMap<Path, NodeList> xpathMatches = new LinkedHashMap<>();
      for (final Path p : fileList) {
        xpathMatches.put(p, findInXml(p.toFile(), expr));
      }

      // Keep only those files where at least one node matches the XPath
      // expression
      final List<Path> keepFiles = xpathMatches.entrySet().stream()
          .filter(x -> x.getValue().getLength() > 0).map(x -> x.getKey())
          .collect(Collectors.toList());
      if (keepFiles.size() == 0) {
        System.err.println(
            "No file contains nodes matching the XPath expression " + query);
        System.exit(1);
      }

      // Copy files from previous step to destination directory
      keepFiles.forEach(f -> {
        try {
          Files.copy(f,
              Paths.get(outDir.getFileName().toString(),
                  f.getFileName().toString()),
              StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException e) {
          // e.printStackTrace();
          System.err.println(
              "An error occurred while copying. Some files may not have been copied to destination.");
          System.exit(1);
        }
      });
    } catch (XPathExpressionException | NullPointerException e) {
      // e.printStackTrace();
      System.err.println("XPath expression cannot be compiled.");
      System.exit(1);
    }
  }
}
