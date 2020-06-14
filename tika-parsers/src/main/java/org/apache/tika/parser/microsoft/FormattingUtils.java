/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.microsoft;

import org.apache.poi.wp.usermodel.CharacterRun;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.tika.sax.XHTMLContentHandler;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STVerticalAlignRun;
import org.xml.sax.SAXException;

import java.util.Deque;
import java.util.EnumSet;
import java.util.Locale;

public class FormattingUtils {
    private FormattingUtils() {
    }

    /**
     * Closes all tags until {@code currentState} contains only tags from {@code desired} set,
     * then open all required tags to reach desired state.
     *
     * @param xhtml        handler
     * @param desired      desired formatting state
     * @param currentState current formatting state (stack of open formatting tags)
     * @throws SAXException pass underlying handler exception
     */
    public static void ensureFormattingState(XHTMLContentHandler xhtml,
                                             EnumSet<Tag> desired,
                                             Deque<Tag> currentState) throws SAXException {
        EnumSet<FormattingUtils.Tag> undesired = EnumSet.complementOf(desired);

        while (!currentState.isEmpty() && currentState.stream().anyMatch(undesired::contains)) {
            xhtml.endElement(currentState.pop().tagName());
        }

        desired.removeAll(currentState);
        for (FormattingUtils.Tag tag : desired) {
            currentState.push(tag);
            xhtml.startElement(tag.tagName());
        }
    }

    /**
     * Closes all formatting tags.
     *
     * @param xhtml           handler
     * @param formattingState current formatting state (stack of open formatting tags)
     * @throws SAXException pass underlying handler exception
     */
    public static void closeStyleTags(XHTMLContentHandler xhtml,
                                      Deque<Tag> formattingState) throws SAXException {
        ensureFormattingState(xhtml, EnumSet.noneOf(Tag.class), formattingState);
    }

    public static EnumSet<Tag> toTags(CharacterRun run) {
        EnumSet<Tag> tags = EnumSet.noneOf(Tag.class);
        if (run.isBold()) {
            tags.add(Tag.B);
        }
        if (run.isItalic()) {
            tags.add(Tag.I);
        }
        if (run.isStrikeThrough()) {
            tags.add(Tag.S);
        }
        if(run instanceof XWPFRun) {
            XWPFRun xwpfRun = (XWPFRun) run;
            if (xwpfRun.getUnderline() != UnderlinePatterns.NONE) {
                tags.add(Tag.U);
            }
            // Sup/Sub on style that is applied to the character run
            XWPFStyles documentStyles = xwpfRun.getParent().getDocument().getStyles();
            String crStyle = xwpfRun.getStyle();// can be empty string
            XWPFStyle style = documentStyles.getStyle(crStyle);
            if(style!=null) {
                CTStyle crCTStyle = style.getCTStyle();
                STVerticalAlignRun.Enum vertAlign = crCTStyle.getRPr().getVertAlign().getVal();
                if (vertAlign == STVerticalAlignRun.SUPERSCRIPT) {
                    tags.add(Tag.SUP);
                }
                if (vertAlign == STVerticalAlignRun.SUBSCRIPT) {
                    tags.add(Tag.SUB);
                }
            }
            // Sup/Sub on actual character run
            if (xwpfRun.getVerticalAlignment() == STVerticalAlignRun.SUPERSCRIPT) {
                tags.add(Tag.SUP);
            }
            if (xwpfRun.getVerticalAlignment() == STVerticalAlignRun.SUBSCRIPT) {
                tags.add(Tag.SUB);
            }
        } else if(run instanceof org.apache.poi.hwpf.usermodel.CharacterRun) {
            org.apache.poi.hwpf.usermodel.CharacterRun hwpfRun = (org.apache.poi.hwpf.usermodel.CharacterRun) run;
            if (hwpfRun.getUnderlineCode() != 0) {
                tags.add(Tag.U);
            }
            // CHPAbstractType.java protected fields:
            // protected final static byte ISS_NONE = 0;
            // protected final static byte ISS_SUPERSCRIPTED = 1;
            // protected final static byte ISS_SUBSCRIPTED = 2;
            if (hwpfRun.getSubSuperScriptIndex() == 1) {
                tags.add(Tag.SUP);
            }
            if (hwpfRun.getSubSuperScriptIndex() == 2) {
                tags.add(Tag.SUB);
            }
        }
        return tags;
    }

    public enum Tag {
        // DON'T reorder elements to avoid breaking tests: EnumSet is iterated in natural order
        // as enum variants are declared
        B, I, S, U, SUP, SUB;

        public String tagName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
