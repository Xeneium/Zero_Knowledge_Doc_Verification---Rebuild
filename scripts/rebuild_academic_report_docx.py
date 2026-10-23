from pathlib import Path
import re
from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.section import WD_SECTION_START
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from pypdf import PdfReader

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'output' / 'pdf' / 'zero_knowledge_document_verification_project_report.pdf'
OUT = ROOT / 'output' / 'docx' / 'zero_knowledge_document_verification_project_report.docx'
OUT.parent.mkdir(parents=True, exist_ok=True)

TITLE = 'ZERO-KNOWLEDGE DOCUMENT VERIFICATION PLATFORM'
CHAPTERS = {
    'CHAPTER 1 INTRODUCTION', 'CHAPTER 2 LITERATURE REVIEW', 'CHAPTER 3 SYSTEM DESIGN',
    'CHAPTER 4 IMPLEMENTATION', 'CHAPTER 5 RESULT AND DISCUSSION',
    'CHAPTER 6 CONCLUSION AND FUTURE ENHANCEMENT', 'CHAPTER 7 REFERENCES'
}
SECTIONS = {
    '1.1 PROBLEM STATEMENT', '1.2 PROJECT OBJECTIVES', '1.3 SUSTAINABLE DEVELOPMENT GOAL (SDG) ALIGNMENT',
    '2.1 JAVA PROGRAMMING LANGUAGE', '2.2 LITERATURE REVIEW', '3.1 OVERALL SYSTEM ARCHITECTURE',
    '3.2 MODULE EXPLANATION', '4.1 SOURCE CODE'
}

def set_cell_border(cell, **kwargs):
    tcPr = cell._tc.get_or_add_tcPr(); borders = tcPr.first_child_found_in('w:tcBorders')
    if borders is None: borders = OxmlElement('w:tcBorders'); tcPr.append(borders)
    for edge in ('top','left','bottom','right'):
        tag = 'w:' + edge; element = borders.find(qn(tag))
        if element is None: element = OxmlElement(tag); borders.append(element)
        element.set(qn('w:val'), kwargs.get('val','single')); element.set(qn('w:sz'), '4'); element.set(qn('w:color'),'D9D9D9')

def style_doc(d):
    sec = d.sections[0]; sec.page_width=Inches(8.5); sec.page_height=Inches(11)
    sec.top_margin=Inches(.72); sec.bottom_margin=Inches(.65); sec.left_margin=Inches(.85); sec.right_margin=Inches(.72)
    normal=d.styles['Normal']; normal.font.name='Times New Roman'; normal._element.rPr.rFonts.set(qn('w:eastAsia'),'Times New Roman'); normal.font.size=Pt(14)
    normal.paragraph_format.line_spacing=1.5; normal.paragraph_format.space_after=Pt(6)
    for name,size,align in [('Title',18,WD_ALIGN_PARAGRAPH.CENTER),('Heading 1',18,WD_ALIGN_PARAGRAPH.CENTER),('Heading 2',16,WD_ALIGN_PARAGRAPH.LEFT)]:
        st=d.styles[name]; st.font.name='Times New Roman'; st._element.rPr.rFonts.set(qn('w:eastAsia'),'Times New Roman'); st.font.size=Pt(size); st.font.bold=True; st.font.color.rgb=RGBColor(0,0,0); st.paragraph_format.alignment=align; st.paragraph_format.space_before=Pt(10); st.paragraph_format.space_after=Pt(10)
    d.styles['Heading 1'].paragraph_format.keep_with_next=True; d.styles['Heading 2'].paragraph_format.keep_with_next=True

def p(d, text='', style=None, align=None, bold=False):
    x=d.add_paragraph(style=style); x.paragraph_format.line_spacing=1.5
    if align is not None: x.alignment=align
    r=x.add_run(text); r.font.name='Times New Roman'; r._element.rPr.rFonts.set(qn('w:eastAsia'),'Times New Roman'); r.font.size=Pt(14 if style not in ('Heading 1','Heading 2','Title') else (18 if style in ('Heading 1','Title') else 16)); r.bold=bold
    if style is None: x.alignment=WD_ALIGN_PARAGRAPH.JUSTIFY
    return x

def break_page(d): d.add_page_break()

def add_front_matter(d, toc):
    # Cover follows the approved sample layout without copying its text.
    p(d, TITLE, 'Title'); p(d, '(Privacy-Preserving Document and Credential Verification)', align=WD_ALIGN_PARAGRAPH.CENTER)
    p(d, 'A PROJECT REPORT', align=WD_ALIGN_PARAGRAPH.CENTER, bold=True); p(d, 'Submitted by', align=WD_ALIGN_PARAGRAPH.CENTER)
    p(d, 'P SAKTHI SABAREESH', align=WD_ALIGN_PARAGRAPH.CENTER, bold=True); p(d, '310625104625', align=WD_ALIGN_PARAGRAPH.CENTER)
    p(d, 'in partial fulfilment for the award of the degree', align=WD_ALIGN_PARAGRAPH.CENTER)
    p(d, 'BACHELOR OF ENGINEERING IN COMPUTER SCIENCE AND ENGINEERING', align=WD_ALIGN_PARAGRAPH.CENTER, bold=True)
    p(d, 'II YEAR CSE', align=WD_ALIGN_PARAGRAPH.CENTER); p(d, 'EASWARI ENGINEERING COLLEGE, RAMAPURAM', align=WD_ALIGN_PARAGRAPH.CENTER, bold=True)
    p(d, 'ANNA UNIVERSITY, CHENNAI', align=WD_ALIGN_PARAGRAPH.CENTER); p(d, '2026 - 2027', align=WD_ALIGN_PARAGRAPH.CENTER); break_page(d)
    p(d, 'BONAFIDE CERTIFICATE', 'Heading 1')
    p(d, 'This is to certify that the project report entitled "Zero-Knowledge Document Verification Platform" is a record of the project work carried out by P Sakthi Sabareesh, Register Number 310625104625, Department of Computer Science and Engineering, Easwari Engineering College, during the academic year 2026 - 2027, under the supervision of Ms. A. Bhagyalakshmi, Assistant Professor, Department of Computer Science and Engineering.')
    t=d.add_table(rows=3, cols=2); t.autofit=True
    entries=[('____________________________','____________________________'),('Ms. A. Bhagyalakshmi','Cloudin'),('Supervisor\nAssistant Professor, CSE','Head of the Department, CSE')]
    for row,vals in zip(t.rows,entries):
        for c,v in zip(row.cells,vals): c.text=v; set_cell_border(c)
    p(d,'Internal Examiner: ____________________    External Examiner: ____________________',align=WD_ALIGN_PARAGRAPH.LEFT); break_page(d)
    p(d,'DECLARATION','Heading 1'); p(d,'I hereby declare that this project report is based on the Zero-Knowledge Document Verification Platform repository and has been prepared for academic project work. The report describes the implemented Java, Spring Boot, React, TypeScript, cryptographic, database, testing and deployment components without importing the topic or technical claims of the sample report.')
    p(d,'Place: Chennai\nDate: ____________________\n\nSignature of the Student: ______________________________\nP Sakthi Sabareesh',align=WD_ALIGN_PARAGRAPH.LEFT); break_page(d)
    p(d,'ABSTRACT','Heading 1'); p(d,'Digital documents and credentials are often shared in full even when a verifier needs to confirm only one fact. This exposes unnecessary personal information and leaves document integrity, issuer authenticity and verification status difficult to evaluate in a consistent workflow.')
    p(d,'The Zero-Knowledge Document Verification Platform is a modular Java application with a React web interface. It combines streaming SHA-256 document digests, RSA-PSS signatures, canonical credential encoding, Pedersen commitments and Bulletproof predicate proofs. The design enables verification of a supported threshold statement without disclosing the raw private attribute.')
    p(d,'The implementation separates issuer, holder, verifier and administrator roles. It uses Java 21, Spring Boot, JDBC, Flyway, PostgreSQL for production, SQLite for local development, React, TypeScript, Vite, Tailwind CSS, Docker and GitHub Actions. The report distinguishes completed features from future work and reserves spaces for final project screenshots.')
    p(d,'Keywords: Zero-Knowledge Proofs, Pedersen Commitment, Bulletproofs, RSA-PSS, SHA-256, Credential Verification, Privacy, Document Integrity.'); break_page(d)
    p(d,'ACKNOWLEDGEMENT','Heading 1'); p(d,'I express my sincere gratitude to the management and Principal of Easwari Engineering College for providing the academic environment and facilities required to complete this project. I thank Cloudin, Head of the Department of Computer Science and Engineering, for institutional encouragement and support.')
    p(d,'I express my deepest gratitude to my supervisor, Ms. A. Bhagyalakshmi, Assistant Professor, Department of Computer Science and Engineering, for her guidance, technical suggestions and feedback throughout the project work. I also acknowledge the open-source communities behind the technologies used in this project and thank my family, classmates and friends for their support.'); p(d,'P Sakthi Sabareesh',align=WD_ALIGN_PARAGRAPH.LEFT); break_page(d)
    p(d,'TABLE OF CONTENTS','Heading 1')
    table=d.add_table(rows=1,cols=3); hdr=table.rows[0].cells
    for c,txt in zip(hdr,['CHAPTER NO.','TITLE','PAGE NO.']): c.text=txt; set_cell_border(c)
    rows=[('', 'ABSTRACT', 'iii'),('', 'ACKNOWLEDGEMENT','iv'),('', 'LIST OF FIGURES','vii'),('1.','INTRODUCTION',toc.get('CHAPTER 1 INTRODUCTION','1')),('', '1.1 PROBLEM STATEMENT',toc.get('1.1 PROBLEM STATEMENT','2')),('', '1.2 PROJECT OBJECTIVES',toc.get('1.2 PROJECT OBJECTIVES','2')),('', '1.3 SUSTAINABLE DEVELOPMENT GOAL (SDG) ALIGNMENT',toc.get('1.3 SUSTAINABLE DEVELOPMENT GOAL (SDG) ALIGNMENT','3')),('2.','LITERATURE REVIEW',toc.get('CHAPTER 2 LITERATURE REVIEW','5')),('', '2.1 JAVA PROGRAMMING LANGUAGE',toc.get('2.1 JAVA PROGRAMMING LANGUAGE','5')),('', '2.2 LITERATURE REVIEW',toc.get('2.2 LITERATURE REVIEW','7')),('3.','SYSTEM DESIGN',toc.get('CHAPTER 3 SYSTEM DESIGN','12')),('', '3.1 OVERALL SYSTEM ARCHITECTURE',toc.get('3.1 OVERALL SYSTEM ARCHITECTURE','12')),('', '3.2 MODULE EXPLANATION',toc.get('3.2 MODULE EXPLANATION','15')),('4.','IMPLEMENTATION',toc.get('CHAPTER 4 IMPLEMENTATION','22')),('', '4.1 SOURCE CODE',toc.get('4.1 SOURCE CODE','22')),('5.','RESULT AND DISCUSSION',toc.get('CHAPTER 5 RESULT AND DISCUSSION','42')),('6.','CONCLUSION AND FUTURE ENHANCEMENT',toc.get('CHAPTER 6 CONCLUSION AND FUTURE ENHANCEMENT','54')),('7.','REFERENCES',toc.get('CHAPTER 7 REFERENCES','59'))]
    for a,b,c in rows:
        cells=table.add_row().cells
        for cell,txt in zip(cells,[a,b,c]): cell.text=txt; set_cell_border(cell)
    break_page(d)
    p(d,'LIST OF FIGURES','Heading 1')
    for line in ['Figure 3.1  Overall system architecture','Figure 3.2  Document registration and verification flow','Figure 3.3  Credential issuance and holder proof flow','Figure 3.4  Trust boundaries and security controls','Figure 4.1  Issuer document upload workflow','Figure 4.2  Public QR verification workflow','Figure 4.3  Holder credential wallet workflow','Figure 4.4  Administrator key and audit workflow','Figure 5.1  Document verification result','Figure 5.2  Predicate verification result']:
        p(d,line,align=WD_ALIGN_PARAGRAPH.LEFT)
    break_page(d)

def normalized(s): return re.sub(r'\s+',' ',s.strip()).upper()

def source_blocks():
    reader=PdfReader(str(SOURCE)); blocks=[]; seen=set()
    for page_no in range(8,len(reader.pages)):
        raw=reader.pages[page_no].extract_text() or ''
        lines=[x.strip() for x in raw.splitlines()]
        # ReportLab extracts chapter titles on two lines; normalise them before
        # applying the document heading hierarchy.
        joined=[]; i=0
        while i < len(lines):
            if re.fullmatch(r'CHAPTER [1-7]', lines[i]) and i+1 < len(lines):
                joined.append(lines[i] + ' ' + lines[i+1]); i += 2
            else:
                joined.append(lines[i]); i += 1
        lines=joined
        if lines and re.fullmatch(r'\d+',lines[0]): lines=lines[1:]
        buf=[]
        for line in lines:
            k=normalized(line)
            if not line: continue
            if k in SECTIONS or k in CHAPTERS:
                if buf: blocks.append(('body',' '.join(buf))); buf=[]
                if k in seen and k in {'2.1 JAVA PROGRAMMING LANGUAGE','2.2 LITERATURE REVIEW'}: continue
                seen.add(k); blocks.append(('chapter' if k in CHAPTERS else 'section',k)); continue
            if re.match(r'^(Figure|FIGURE)\s+\d+\.\d+',line) or 'IMAGE TO BE INSERTED' in k or 'RESERVED SPACE' in k:
                if buf: blocks.append(('body',' '.join(buf))); buf=[]
                blocks.append(('figure',line)); continue
            # A sentence-ending wrapped line marks a PDF paragraph reasonably well.
            buf.append(line)
            if re.search(r'[.!?]$',line): blocks.append(('body',' '.join(buf))); buf=[]
        if buf: blocks.append(('body',' '.join(buf)))
    return blocks

def add_body(d):
    for kind,text in source_blocks():
        if kind=='chapter':
            # Chapter 1 uses the requested standalone opening; all chapter names are centred.
            p(d,text,'Heading 1'); break_page(d)
        elif kind=='section': p(d,text,'Heading 2')
        elif kind=='figure':
            p(d,'[Reserved space for project screenshot or diagram: '+text+']',align=WD_ALIGN_PARAGRAPH.CENTER)
            for _ in range(5): p(d,'',align=WD_ALIGN_PARAGRAPH.CENTER)
        else:
            clean=re.sub(r'\s+',' ',text).strip()
            if len(clean)>20: p(d,clean)

def build(toc=None):
    d=Document(); style_doc(d); add_front_matter(d,toc or {}); add_body(d); d.save(OUT)

if __name__=='__main__': build()
