package stirling.software.SPDF.service.facilis;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import lombok.extern.slf4j.Slf4j;

/**
 * Parses a FACILIS .lay XML file into {@link FacilisLayout}. Only fields the imposition executor
 * and the listing API need are extracted; unused elements (color tables, grid lines, EPS marks
 * etc.) are skipped.
 *
 * <p>FACILIS encodes lengths as integer × 10000 in the {@code int} attribute of {@code <Top>},
 * {@code <Size>} etc. The {@code unit} attribute (usually {@code "point"} or {@code "mm"}) tells
 * us how to interpret it. We always normalise to PDF points.
 */
@Slf4j
public final class FacilisLayoutParser {

    private static final float MM_TO_PT = 2.834645669f;
    private static final float SCALE_DIVISOR = 10000f;

    private FacilisLayoutParser() {}

    public static FacilisLayout parse(Path file, String relativeId, String category)
            throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return parse(in, relativeId, category);
        }
    }

    public static FacilisLayout parse(InputStream in, String relativeId, String category)
            throws IOException {
        Document doc;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            // Hardening: disable external entities & DTD loading.
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            doc = db.parse(in);
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Failed to parse FACILIS layout: " + relativeId, e);
        }

        // FACILIS v4.0 Supremo wraps the layout deeply:
        //   <FXDBF><LocalDB>...<DBItemData><DBItem><LAYOUT>...
        // Older bare-LAYOUT files have it as the document root. Try direct child first, then
        // fall back to a namespace-agnostic descendant search so both shapes work.
        Element layout = firstByLocalName(doc.getDocumentElement(), "LAYOUT");
        if (layout == null) {
            NodeList descendants =
                    doc.getDocumentElement().getElementsByTagNameNS("*", "LAYOUT");
            if (descendants.getLength() > 0) {
                layout = (Element) descendants.item(0);
            }
        }
        if (layout == null) {
            throw new IOException("No <LAYOUT> element in " + relativeId);
        }
        String name = layout.getAttribute("name");

        Element frontPlate = firstByLocalName(layout, "FrontPlate");
        Element backPlate = firstByLocalName(layout, "BackPlate");
        Element sLayout = firstByLocalName(layout, "SLayout");

        float plateW = 0f;
        float plateH = 0f;
        if (frontPlate != null) {
            Element size = firstByLocalName(frontPlate, "Size");
            if (size != null) {
                plateW = readDimension(firstByLocalName(size, "H"));
                plateH = readDimension(firstByLocalName(size, "V"));
            }
        }

        int pagesH = 1;
        int pagesV = 1;
        String bindSide = "";
        String flipDir = "";
        float finishW = 0f;
        float finishH = 0f;
        float bleedTop = 0f;
        float bleedBottom = 0f;
        float bleedLeft = 0f;
        float bleedRight = 0f;
        List<FacilisPageSlot> frontPages = List.of();
        List<FacilisPageSlot> backPages = List.of();

        if (sLayout != null) {
            pagesH = parseInt(sLayout.getAttribute("pages_h"), 1);
            pagesV = parseInt(sLayout.getAttribute("pages_v"), 1);
            bindSide = sLayout.getAttribute("bind_side");
            flipDir = sLayout.getAttribute("flip_dir");

            Element finishSize = firstByLocalName(sLayout, "FinishSize");
            if (finishSize != null) {
                finishW = readDimension(firstByLocalName(finishSize, "H"));
                finishH = readDimension(firstByLocalName(finishSize, "V"));
            }

            Element bleed = firstByLocalName(sLayout, "BleedWidths");
            if (bleed != null) {
                bleedTop = readDimension(firstByLocalName(bleed, "Top"));
                bleedBottom = readDimension(firstByLocalName(bleed, "Bottom"));
                bleedLeft = readDimension(firstByLocalName(bleed, "Left"));
                bleedRight = readDimension(firstByLocalName(bleed, "Right"));
            }

            frontPages = readPagesForSide(sLayout, "Front");
            backPages = readPagesForSide(sLayout, "Back");
        }

        return new FacilisLayout(
                relativeId,
                name == null || name.isBlank() ? relativeId : name,
                category == null ? "" : category,
                plateW,
                plateH,
                pagesH,
                pagesV,
                finishW,
                finishH,
                bleedTop,
                bleedBottom,
                bleedLeft,
                bleedRight,
                bindSide == null ? "" : bindSide,
                flipDir == null ? "" : flipDir,
                frontPages,
                backPages);
    }

    private static List<FacilisPageSlot> readPagesForSide(Element sLayout, String side) {
        // Find <SLayoutFB side="Front|Back"> then iterate <LayoutItem_Pages>/<Page>.
        NodeList fbList = sLayout.getElementsByTagNameNS("*", "SLayoutFB");
        for (int i = 0; i < fbList.getLength(); i++) {
            Element fb = (Element) fbList.item(i);
            if (!side.equalsIgnoreCase(fb.getAttribute("side"))) {
                continue;
            }
            List<FacilisPageSlot> slots = new ArrayList<>();
            NodeList pagesContainers = fb.getElementsByTagNameNS("*", "LayoutItem_Pages");
            for (int j = 0; j < pagesContainers.getLength(); j++) {
                Element container = (Element) pagesContainers.item(j);
                NodeList pages = container.getElementsByTagNameNS("*", "Page");
                for (int k = 0; k < pages.getLength(); k++) {
                    Element page = (Element) pages.item(k);
                    // Only direct children of the container, not nested ones.
                    if (page.getParentNode() != container) {
                        continue;
                    }
                    slots.add(parsePageSlot(page));
                }
            }
            return slots;
        }
        return List.of();
    }

    private static FacilisPageSlot parsePageSlot(Element page) {
        int pageNum = parseInt(page.getAttribute("page_num"), 0);
        int posH = parseInt(page.getAttribute("pos_h"), 0);
        int posV = parseInt(page.getAttribute("pos_v"), 0);

        Element size = firstByLocalName(page, "Size");
        float w = size != null ? readDimension(firstByLocalName(size, "H")) : 0f;
        float h = size != null ? readDimension(firstByLocalName(size, "V")) : 0f;

        Element scale = firstByLocalName(page, "Scale");
        float scaleH = scale != null ? parseFloat(scale.getAttribute("h"), 1f) : 1f;
        float scaleV = scale != null ? parseFloat(scale.getAttribute("v"), 1f) : 1f;

        Element orientation = firstByLocalName(page, "Orientation");
        String orientStr = orientation != null ? orientation.getAttribute("value") : "Up";

        // Position is item_ref="BL" with V_Left / H_Bottom offsets — bottom-left of the slot.
        float offX = 0f;
        float offY = 0f;
        Element pos = firstByLocalName(page, "Position");
        if (pos != null) {
            Element hEl = firstByLocalName(pos, "H");
            Element vEl = firstByLocalName(pos, "V");
            if (hEl != null) {
                offX = readDimension(firstByLocalName(hEl, "Offset"));
            }
            if (vEl != null) {
                offY = readDimension(firstByLocalName(vEl, "Offset"));
            }
        }

        Element bleed = firstByLocalName(page, "BleedWidths");
        Float bt = null, bb = null, bl = null, br = null;
        if (bleed != null) {
            bt = readDimension(firstByLocalName(bleed, "Top"));
            bb = readDimension(firstByLocalName(bleed, "Bottom"));
            bl = readDimension(firstByLocalName(bleed, "Left"));
            br = readDimension(firstByLocalName(bleed, "Right"));
        }

        return new FacilisPageSlot(
                pageNum,
                posH,
                posV,
                w,
                h,
                offX,
                offY,
                scaleH,
                scaleV,
                orientStr,
                bt,
                bb,
                bl,
                br);
    }

    /**
     * Reads a FACILIS dimension element ({@code <H type="Decimal" int="N" unit="point|mm"/>}) and
     * returns the value in PDF points. The {@code num/den} fractional parts are ignored — every
     * sample observed has them at {@code 0/1}.
     */
    private static float readDimension(Element el) {
        if (el == null) {
            return 0f;
        }
        long intVal = parseLong(el.getAttribute("int"), 0L);
        float value = intVal / SCALE_DIVISOR;
        String unit = el.getAttribute("unit");
        if (unit == null || unit.isBlank() || "point".equalsIgnoreCase(unit) || "pt".equalsIgnoreCase(unit)) {
            return value;
        }
        if ("mm".equalsIgnoreCase(unit)) {
            return value * MM_TO_PT;
        }
        log.debug("FACILIS layout: unrecognised unit '{}', treating as points", unit);
        return value;
    }

    private static Element firstByLocalName(Element parent, String localName) {
        if (parent == null) {
            return null;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE
                    && localName.equals(n.getLocalName() == null ? n.getNodeName() : n.getLocalName())) {
                return (Element) n;
            }
        }
        return null;
    }

    private static int parseInt(String s, int fallback) {
        if (s == null || s.isBlank()) return fallback;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseLong(String s, long fallback) {
        if (s == null || s.isBlank()) return fallback;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float parseFloat(String s, float fallback) {
        if (s == null || s.isBlank()) return fallback;
        try {
            return Float.parseFloat(s.trim().toLowerCase(Locale.ROOT));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
