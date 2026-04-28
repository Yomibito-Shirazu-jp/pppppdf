package stirling.software.SPDF.service.facilis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class FacilisLayoutParserTest {

    /**
     * Real-shape FACILIS .lay snippet (truncated to the elements the parser cares about).
     * Confirms namespace handling, the int×10000 scaling rule, page slot extraction, and the
     * Front/Back partitioning.
     */
    private static final String SAMPLE_LAY =
            """
            <?xml version="1.0" encoding="UTF-8" standalone="yes" ?>
            <FXDBF xmlns="http://www.facilissoftware.com/schema/facilis-v4" version="4.0">
              <DBItemData root_elm_id="x">
                <DBItem id="x">
                  <LAYOUT id="x" name="A2 2面（A4）16p7号機.lay" version="4.0">
                    <FrontPlate>
                      <Size>
                        <H type="Decimal" int="24548030" num="0" den="1" unit="point"/>
                        <V type="Decimal" int="17177950" num="0" den="1" unit="point"/>
                      </Size>
                    </FrontPlate>
                    <BackPlate>
                      <Size>
                        <H type="Decimal" int="24548030" num="0" den="1" unit="point"/>
                        <V type="Decimal" int="17177950" num="0" den="1" unit="point"/>
                      </Size>
                    </BackPlate>
                    <SLayout flip_dir="TopToBottom" pages_h="2" pages_v="1" bind_side="Other">
                      <FinishSize name="Custom Size">
                        <H type="Decimal" int="12075590" num="0" den="1" unit="point"/>
                        <V type="Decimal" int="17177950" num="0" den="1" unit="point"/>
                      </FinishSize>
                      <BleedWidths same_all="true">
                        <Top type="Decimal" int="85040" num="0" den="1" unit="point"/>
                        <Bottom type="Decimal" int="85040" num="0" den="1" unit="point"/>
                        <Left type="Decimal" int="85040" num="0" den="1" unit="point"/>
                        <Right type="Decimal" int="85040" num="0" den="1" unit="point"/>
                      </BleedWidths>
                      <SLayoutFB side="Front">
                        <SLayoutItemList count="1">
                          <LayoutItem_Pages name="ページ" page_count="2">
                            <Page page_num="1" pos_h="1" pos_v="1">
                              <Size>
                                <H type="Decimal" int="12075590" num="0" den="1" unit="point"/>
                                <V type="Decimal" int="17177950" num="0" den="1" unit="point"/>
                              </Size>
                              <Scale h="1.0000" v="1.0000"/>
                              <Orientation value="Up"/>
                              <Position item_ref="BL">
                                <H base_type="V_Left">
                                  <Offset type="Decimal" int="340160" num="0" den="1" unit="point"/>
                                </H>
                                <V base_type="H_Bottom">
                                  <Offset type="Decimal" int="269290" num="0" den="1" unit="point"/>
                                </V>
                              </Position>
                            </Page>
                            <Page page_num="3" pos_h="2" pos_v="1">
                              <Size>
                                <H type="Decimal" int="12075590" num="0" den="1" unit="point"/>
                                <V type="Decimal" int="17177950" num="0" den="1" unit="point"/>
                              </Size>
                              <Scale h="1.0000" v="1.0000"/>
                              <Orientation value="Up"/>
                              <Position item_ref="BL">
                                <H base_type="V_Left">
                                  <Offset type="Decimal" int="12812600" num="0" den="1" unit="point"/>
                                </H>
                                <V base_type="H_Bottom">
                                  <Offset type="Decimal" int="269290" num="0" den="1" unit="point"/>
                                </V>
                              </Position>
                            </Page>
                          </LayoutItem_Pages>
                        </SLayoutItemList>
                      </SLayoutFB>
                      <SLayoutFB side="Back">
                        <SLayoutItemList count="1">
                          <LayoutItem_Pages name="ページ" page_count="2">
                            <Page page_num="2" pos_h="1" pos_v="1">
                              <Size>
                                <H type="Decimal" int="12075590" num="0" den="1" unit="point"/>
                                <V type="Decimal" int="17177950" num="0" den="1" unit="point"/>
                              </Size>
                              <Scale h="1.0000" v="1.0000"/>
                              <Orientation value="Up"/>
                              <Position item_ref="BL">
                                <H base_type="V_Left">
                                  <Offset type="Decimal" int="340160" num="0" den="1" unit="point"/>
                                </H>
                                <V base_type="H_Bottom">
                                  <Offset type="Decimal" int="269290" num="0" den="1" unit="point"/>
                                </V>
                              </Position>
                            </Page>
                            <Page page_num="4" pos_h="2" pos_v="1">
                              <Size>
                                <H type="Decimal" int="12075590" num="0" den="1" unit="point"/>
                                <V type="Decimal" int="17177950" num="0" den="1" unit="point"/>
                              </Size>
                              <Scale h="1.0000" v="1.0000"/>
                              <Orientation value="Up"/>
                              <Position item_ref="BL">
                                <H base_type="V_Left">
                                  <Offset type="Decimal" int="12812600" num="0" den="1" unit="point"/>
                                </H>
                                <V base_type="H_Bottom">
                                  <Offset type="Decimal" int="269290" num="0" den="1" unit="point"/>
                                </V>
                              </Position>
                            </Page>
                          </LayoutItem_Pages>
                        </SLayoutItemList>
                      </SLayoutFB>
                    </SLayout>
                  </LAYOUT>
                </DBItem>
              </DBItemData>
            </FXDBF>
            """;

    @Test
    void parsesLayoutHeaderAndDimensions() throws Exception {
        FacilisLayout layout =
                FacilisLayoutParser.parse(
                        new ByteArrayInputStream(SAMPLE_LAY.getBytes(StandardCharsets.UTF_8)),
                        "Layout/SignatureLayout/A版/その他/A2 2面（A4）16p7号機.lay",
                        "A版/その他");

        assertThat(layout.id()).isEqualTo("Layout/SignatureLayout/A版/その他/A2 2面（A4）16p7号機.lay");
        assertThat(layout.name()).isEqualTo("A2 2面（A4）16p7号機.lay");
        assertThat(layout.category()).isEqualTo("A版/その他");
        // 24548030 / 10000 = 2454.803 pt
        assertThat(layout.plateWidthPt()).isEqualTo(2454.803f);
        assertThat(layout.plateHeightPt()).isEqualTo(1717.795f);
        assertThat(layout.pagesH()).isEqualTo(2);
        assertThat(layout.pagesV()).isEqualTo(1);
        assertThat(layout.bindSide()).isEqualTo("Other");
        assertThat(layout.flipDir()).isEqualTo("TopToBottom");
        // 85040 / 10000 = 8.504 pt
        assertThat(layout.bleedTopPt()).isEqualTo(8.504f);
    }

    @Test
    void parsesPageSlotsForBothSides() throws Exception {
        FacilisLayout layout =
                FacilisLayoutParser.parse(
                        new ByteArrayInputStream(SAMPLE_LAY.getBytes(StandardCharsets.UTF_8)),
                        "x.lay",
                        "");

        assertThat(layout.frontPages()).hasSize(2);
        assertThat(layout.backPages()).hasSize(2);

        FacilisPageSlot front1 = layout.frontPages().get(0);
        assertThat(front1.pageNum()).isEqualTo(1);
        assertThat(front1.posH()).isEqualTo(1);
        assertThat(front1.posV()).isEqualTo(1);
        // 12075590 / 10000 = 1207.559 pt
        assertThat(front1.widthPt()).isEqualTo(1207.559f);
        assertThat(front1.heightPt()).isEqualTo(1717.795f);
        // 340160 / 10000 = 34.016 pt
        assertThat(front1.offsetXPt()).isEqualTo(34.016f);
        assertThat(front1.offsetYPt()).isEqualTo(26.929f);
        assertThat(front1.orientation()).isEqualTo("Up");
        assertThat(front1.scaleH()).isEqualTo(1.0f);

        FacilisPageSlot front2 = layout.frontPages().get(1);
        assertThat(front2.pageNum()).isEqualTo(3);
        assertThat(front2.posH()).isEqualTo(2);
        // 12812600 / 10000 = 1281.26 pt
        assertThat(front2.offsetXPt()).isEqualTo(1281.26f);

        // Back side preserves spread numbering: pages 2 and 4 face each other on the back plate.
        assertThat(layout.backPages().get(0).pageNum()).isEqualTo(2);
        assertThat(layout.backPages().get(1).pageNum()).isEqualTo(4);
    }

    @Test
    void mmUnitConvertedToPoints() throws Exception {
        String mmSample =
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes" ?>
                <FXDBF xmlns="http://www.facilissoftware.com/schema/facilis-v4">
                  <DBItemData root_elm_id="x"><DBItem id="x">
                    <LAYOUT id="x" name="mm-test">
                      <FrontPlate>
                        <Size>
                          <H type="Decimal" int="100000" num="0" den="1" unit="mm"/>
                          <V type="Decimal" int="100000" num="0" den="1" unit="mm"/>
                        </Size>
                      </FrontPlate>
                      <SLayout pages_h="1" pages_v="1"/>
                    </LAYOUT>
                  </DBItem></DBItemData>
                </FXDBF>
                """;

        FacilisLayout layout =
                FacilisLayoutParser.parse(
                        new ByteArrayInputStream(mmSample.getBytes(StandardCharsets.UTF_8)),
                        "mm.lay",
                        "");

        // 100000 / 10000 = 10mm → 10 * 2.834645669 ≈ 28.346 pt
        assertThat(layout.plateWidthPt()).isCloseTo(28.346f, org.assertj.core.api.Assertions.within(0.01f));
    }
}
