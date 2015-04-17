package resources.groovy

import org.apache.commons.lang3.StringUtils
import ru.naumen.ccamcore.workflow.stages.CCAMStage
import ru.naumen.ccamext.catalog.TextTemplateCatalogItem
import ru.naumen.common.treeutils.HierarchyConstructionException
import ru.naumen.common.treeutils.IHierarchyNode
import ru.naumen.common.utils.CollectionUtils
import ru.naumen.common.utils.FileUtils
import ru.naumen.core.catalogsengine.ICoreCatalog
import ru.naumen.core.catalogsengine.ICoreCatalogItem
import ru.naumen.core.workflow.stages.actions.ActionBase
import ru.naumen.core.workflow.stages.actions.ScriptedAction
//import ru.naumen.ccamcore.glue.GlueFacade
//import ru.naumen.fcntp.glue.GlueSameSearcher
import ru.naumen.fcntp.groovy.catalog.GroovyScriptsCatalogItem
import ru.naumen.fx.interfaces.FxException

import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import javax.xml.bind.annotation.XmlAccessType
import javax.xml.bind.annotation.XmlAccessorType
import javax.xml.bind.annotation.XmlRootElement
import java.security.MessageDigest

/**
 * Created by IntelliJ IDEA.
 * User: Andrew F. Podkolzin
 * Date: 15.01.15
 * Time: 12:00
 * Since: 
 *
 */

/*
root = "/home/apodkolzin-mint/fcntp-github/glue-local/"
prefix = "local://"
*/

class Utils
{

    def static Hashes hashes = new Hashes()
    def static String root
    def static String prefix

    def List<String> path(IHierarchyNode item)
    {
        def res = []
        while (item)
        {
            def parent = item.hierarchyParent
            def title = item.displayableTitle
            def ptitle = parent?.displayableTitle
            title = StringUtils.removeStart(title, ptitle + "_")
            res.add(title)
            item = parent
        }
        res
    }

    def bytes(Object obj)
    {
        if (obj instanceof String)
            return obj.bytes
        else if (obj instanceof byte[])
            return obj
        else if (obj instanceof Meta) {
            StringWriter writer = new StringWriter();
            JAXBContext context = JAXBContext.newInstance(obj.class);
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8"); //NOI18N
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            marshaller.marshal(obj, writer)
            return writer.toString().bytes
        }
        return new byte[0]
    }

    def String md5sum(byte[] content){
        MessageDigest digest = MessageDigest.getInstance("MD5");
        digest.update(content)
        new BigInteger(1, digest.digest()).toString(16)
    }
}

class FileWriter extends Utils{
    def String dir
    def String name

    FileWriter(String dir, String name) {
        this.dir = root + dir
        this.name = name
    }


    def void write(Object obj, String ext) {
        byte[] content = bytes(obj)
        if (content && content.length > 0) {
            FileUtils.createDirIfNotExists(dir)
            FileUtils.writeFile(file(ext), content)
        }

    }

    def String file(String ext) {
        dir + "/" + name + "." + ext
    }
}

@XmlAccessorType( XmlAccessType.FIELD )
@XmlRootElement(name = "Meta")
class Meta{
    def String id
    def String title = ""
    def String titleEn = ""
    def String groovyId = ""
    def String birtId = ""
    def List<String> keywords = []
    def String uuid = ""

}

class Hashes extends Utils{
    def map = [:]

    def String put(String id, Object obj){
        byte[] content = bytes(obj)
        if (content == null || content.length == 0)
            return null
        def md5sum = md5sum(content)
        if (this.map.containsKey(md5sum))
            this.map[md5sum]
        else {
            def res = glueSame(content)
            this.map[md5sum] = StringUtils.isEmpty(res) ? id : res
            res
        }
    }

    def String glueSame(byte[] content) {
        null //StringUtils.trimToNull(GlueFacade.get().searcher(GlueSameSearcher).findByContent(content))
    }
}

class Unit extends Utils {
    def List<String> path
    def String id = ""
    def String name = ""
    def String title = ""
    def String titleEn = ""
    def Object selectScript
    def Object mainScript
    def List templates
    def String uuid = ""

    def dir() { StringUtils.join(path.reverse(), "/") }

    def id() { prefix + id}

    def Object groovy() { mainScript }

    def Object pregroovy() { selectScript }

    def Object birt() {
        if (templates != null) for (ritem in templates)
            return ritem[0]
    }

    def List abirt() {
        def i = 0
        def res = []
        if (templates != null) for (ritem in templates) { if (i > 0) res.add(ritem[0]); i++ }
    }

    def Meta meta() {
        if (StringUtils.isEmpty(mainScript) && CollectionUtils.isEmptyCollection(templates))
            return null

        def meta = new Meta()
        meta.id = id()
        meta.title = title
        meta.titleEn = titleEn
        meta.groovyId = hashes.put(id(), groovy())
        meta.birtId = hashes.put(id(), birt())
        meta.keywords.addAll(path)
        meta.uuid = uuid
        meta
    }
}

class UnitWriter {
    def List res
    def Unit unit
    def FileWriter creator

    UnitWriter(Unit unit) {
        this.unit = unit
        this.creator = new FileWriter(unit.dir(), unit.name)
    }

    def Object apply() {

        def meta = unit.meta()
        if (meta == null) return null

        create(unit.groovy(), "groovy", meta.groovyId)
        create(unit.birt(), "rptdesign", meta.birtId)
        create(meta,"meta.xml", null)

        create(unit.pregroovy(), "pregroovy", null)
        def i = 1; for (def o in unit.abirt()) { create(o, "rptdesign" + (i++), null) }

        res
    }

    private def void create(Object obj, String ext, String ref) {
        if (StringUtils.isEmpty(ref))
            creator.write(obj, ext)
        else
            res = [ext, unit.id(), ref]
    }
}


class StagesWrapper implements IHierarchyNode {
    def Object obj;

    StagesWrapper(Object obj) {
        this.obj = obj
    }

    @Override
    IHierarchyNode getHierarchyParent() {
        def parent = null
        if (obj instanceof ActionBase)
            parent = ru.naumen.fx.objectloader.PrefixObjectLoaderFacade.getObjectByUUID(obj.ownerIdDeeply)
        else if (obj instanceof CCAMStage)
            parent = obj.getUIParent(null)
        else if (obj instanceof IHierarchyNode)
            parent = obj.hierarchyParent
        parent == null ? null : new StagesWrapper(parent)
    }

    @Override
    String getDisplayableTitle() throws FxException {
        if (obj instanceof ICoreCatalog || obj instanceof ICoreCatalogItem)
            return obj.code
        if (obj instanceof CCAMStage)
            return obj.identificator
        return  obj.displayableTitle
    }
}

def List<Unit> listTextCatalogs() {
    helper.getCatalog("textTemplateCatalog").listItems().findAll{it.useReporter}.collect{TextTemplateCatalogItem item->
        def unit = new Unit()
        unit.id = "textTemplate:" + item.code
        unit.name = item.code
        unit.path = unit.path(item)
        unit.title = item.title
        unit.selectScript =  item.reportData?.reportSelectScript
        unit.mainScript = item.reportData?.sharedDataScript
        unit.templates = item.reportData?.items.collect {[it.reportFile, it.itemName]}
        unit.uuid = item.UUID
        unit
    }
}

def List<Unit> listStages(){
    def map = [:]

    def name = {String key ->
        code = map.getOrDefault(key, code)
        map[key] = code + 1
    }

    helper.select("from ScriptedAction where script is not null").collect{ScriptedAction item->
        def unit = new Unit()
        def path = unit.path(new StagesWrapper(item))
        unit.name = name(path[1] + "_" + path[2])
        unit.id = "stages:" + unit.name
        path.remove(0)
        path.add("Категории")
        unit.path = path
        unit.title = item.title
        unit.mainScript = item.script
        unit.uuid = item.UUID
        unit
    }
}

def List<Unit> listCatalog(){
    helper.getCatalog("GroovyScripts").listItems().collect{GroovyScriptsCatalogItem item->
        def unit = new Unit()
        unit.id = "scripts:" + item.code
        unit.name = item.code
        unit.path = unit.path(item)
        unit.title = item.title
        unit.mainScript = item.scriptText
        unit.uuid = item.UUID
        unit
    }
}

def List init(List<Unit> units) {units.collect{unit-> new UnitWriter(unit).apply()} }

Utils.root = root
Utils.prefix = prefix

def res = init(listTextCatalogs() + listStages() + listCatalog()).findAll{it != null}
res