import com.formdev.flatlaf.FlatDarkLaf
import com.google.gson.*
import os.Path

import java.io.File
import javax.swing.plaf.basic.BasicComboBoxEditor
import javax.swing.{JComboBox, JFileChooser, JOptionPane}
import scala.collection.immutable.ArraySeq
import scala.io.Source
import scala.jdk.CollectionConverters.*
import scala.swing.*
import scala.swing.GridBagPanel.Fill
import scala.swing.event.ButtonClicked

val gson: Gson = GsonBuilder().setLenient().setPrettyPrinting().serializeNulls().create()
var configClass: Class[? <: Config] = (new Config).getClass
val fabricVersionArrayClass: Class[? <: Array[FabricVersion]] = new Array[FabricVersion](0).getClass
val jsonObjectClass: Class[? <: JsonObject] = (new JsonObject).getClass
var config: Config = readConfig()
var selectedFolders: Option[Seq[Path]] = None

def readConfig(): Config = {
  val path = getConfigPath
  if (!os.exists(path)) Config() else gson.fromJson(os.read(path), configClass)
}

def writeConfig(config: Config): Unit = {
  os.write.over(getConfigPath, gson.toJson(config), createFolders = true)
}

def getConfigPath: Path = Option(System.getenv("XDG_CONFIG_HOME"))
  .map(Path(_))
  .getOrElse(os.home / ".config") / "fabric-updater" / "config.json"

class FabricVersion(version: String, stable: Boolean) {
  override def toString: String = if (stable) "✨ " + version + " ✨" else version

  def getVersion: String = version
}

def downloadFabricVersions(): Option[Seq[FabricVersion]] = {
  var src: Source = null
  try {
    src = Source.fromURL("https://meta.fabricmc.net/v2/versions/loader")
    val str = src.mkString
    Some(ArraySeq(gson.fromJson(str, fabricVersionArrayClass) *))
  } catch {
    case e: Exception =>
      e.printStackTrace()
      None
  } finally {
    src.close()
  }

}

class Config(var filepath: String | Null = null) {
  def getFilePath: Option[Path] = {
    Option(filepath).map(Path(_))
  }

  def setFilePath(filepath: Path): Unit = {
    this.filepath = filepath.toString
    writeConfig(this)
  }
}

object FabricUpdater extends SimpleSwingApplication {
  FlatDarkLaf.setup

  private val fileButton = new Button("Select Instances")
  fileButton.reactions += {
    case ButtonClicked(_) =>
      val selector = new JFileChooser(config.getFilePath.map(_.toIO).orNull)
      selector.setMultiSelectionEnabled(true)
      selector.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY)
      val companion = JComboBox()
      val textField = SwingUtils.getDescendantsOfType(companion.getClass, selector).head
      textField.setEditor(new BasicComboBoxEditor.UIResource {
        override def getItem: Object = {
          try {
            new File(super.getItem.asInstanceOf[String])
          } catch {
            case e: Exception => super.getItem
          }
        }
      })
      textField.setEditable(true)
      if (selector.showOpenDialog(panel.contents.head.peer) == JFileChooser.APPROVE_OPTION) {
        val files = selector.getSelectedFiles.map(Path(_))
        config.setFilePath(files.head / os.up)
        selectedFolders = Some(files.toSeq)
        updateFolderText(selectedFolders)
      }
  }

  private val foldersList = new Label
  updateFolderText(None)

  private def updateFolderText(folders: Option[Seq[Path]]): Unit = {
    foldersList.text = folders
      .map(_.map(_.last))
      .getOrElse(Seq("No items are selected"))
      .take(3)
      .reduce((x, y) => x + ", " + y) + (if (folders.map(_.length).getOrElse(0) > 3) ", ..." else "")
  }

  private val versions = downloadFabricVersions()
  if (versions.isEmpty) {
    JOptionPane.showMessageDialog(null, "could not download fabric metadata, please reopen program")
  }
  private val versionDropdown: ComboBox[FabricVersion] = new ComboBox[FabricVersion](versions.getOrElse(Seq.empty))

  private val downloadButton = new Button("Update Fabric")
  downloadButton.reactions += {
    case ButtonClicked(_) =>
      selectedFolders.foreach(_.foreach(f => {
        val f1 = f / "mmc-pack.json"
        if (os.exists(f1)) {
          val data = gson.fromJson(os.read(f1), jsonObjectClass)
          Option(data.get("components"))
            .flatMap(
              _.getAsJsonArray
                .iterator
                .asScala
                .find(a => {
                  val b = Option(a.getAsJsonObject.get("cachedName")).map(_.getAsString).getOrElse("") == "Fabric Loader"
                  val c = Option(a.getAsJsonObject.get("uid")).map(_.getAsString).getOrElse("") == "net.fabricmc.fabric-loader"
                  b && c
                })
            )
            .map(_.getAsJsonObject)
            .foreach(a => {
              Seq("cachedVersion", "version").foreach(b => Option(a.get(b)).foreach(_ => a.add(b, new JsonPrimitive(versionDropdown.selection.item.getVersion))))
            })
          os.write.over(f1, gson.toJson(data))
        }
      }))
  }

  private val panel: GridBagPanel = new GridBagPanel {
    val c = new Constraints
    c.insets = new Insets(5, 5, 5, 3)
    c.ipady = 3
    c.weightx = .5
    c.weighty = .5

    c.gridx = 0
    c.gridy = 0
    layout(fileButton) = c

    c.insets.left = 3
    c.gridx = 1
    c.gridy = 0
    layout(versionDropdown) = c

    c.insets.right = 5
    c.gridx = 2
    c.gridy = 0
    layout(downloadButton) = c

    c.insets.left = 5
    c.insets.bottom = 10
    c.gridx = 0
    c.gridy = 1
    c.gridwidth = 3
    c.fill = Fill.Horizontal
    layout(foldersList) = c
  }

  override def top: MainFrame = new MainFrame {
    title = "Fabric Updater"
    contents = panel
  }
}
