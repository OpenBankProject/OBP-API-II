package code.api.util

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.{DataHolder, MutableDataSet}
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.ext.typographic.TypographicExtension
import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.emoji.EmojiExtension
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension
import com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension
import com.vladsch.flexmark.ext.gitlab.GitLabExtension
import com.vladsch.flexmark.parser.ParserEmulationProfile

import java.util.Arrays

object PegdownOptions {
  // Create the options for the parser and renderer
  private val OPTIONS: DataHolder = {
    val options = new MutableDataSet()
    // Enable extensions //TODO 
//    options.set(Parser.EXTENSIONS, Arrays.asList(
//      TablesExtension.create(),
//      StrikethroughExtension.create(),
//      TypographicExtension.create(),
//      AutolinkExtension.create(),
//      EmojiExtension.create(),
//      TaskListExtension.create(),
//      AnchorLinkExtension.create(),
//      YamlFrontMatterExtension.create(),
//      GitLabExtension.create()
//    ))
    options
  }

  private val PARSER: Parser = Parser.builder(OPTIONS).build
  private val RENDERER: HtmlRenderer = HtmlRenderer.builder(OPTIONS).build

  // Convert Pegdown markdown to HTML with tweaks
  def convertPegdownToHtmlTweaked(description: String): String = {
    val document: Node = PARSER.parse(convertImgTag(description.stripMargin))
    RENDERER.render(document)
      .replaceAll("&ldquo", "&quot")
      .replaceAll("&rdquo", "&quot")
      .replaceAll("&rsquo;", "'")
      .replaceAll("&lsquo;;", "'")
      .replaceAll("&amp;;", "&")
      .replaceAll("&lsquo;", "'")
      .replaceAll("&hellip;", "...")
  }

  // Convert markdown image syntax to HTML img tag
  private def convertImgTag(markdown: String): String = markdown.stripMargin
    .replaceAll(
      """!$$(.*)$$$(.*) =(.*?)x(.*?)$""",
      """<img alt="$1" src="$2" width="$3" height="$4" />"""
    )

  // Convert GitHub Docs markdown to HTML
  def convertGitHubDocMarkdownToHtml(description: String): String = {
    val options = new MutableDataSet()
    options.setFrom(ParserEmulationProfile.GITHUB_DOC)
    val parser = Parser.builder(options).build
    val renderer = HtmlRenderer.builder(options).build
    val document = parser.parse(description.stripMargin)
    renderer.render(document)
  }
}