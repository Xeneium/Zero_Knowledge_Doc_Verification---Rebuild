"""
Generates synthetic, watermarked PDF fixtures for ZKDoc Platform demonstrations.
Creates valid PDF 1.4 documents without requiring third-party libraries.
"""
import hashlib
import os

def create_simple_pdf(title, recipient, organization, details, watermark_text):
    content_stream = f"""BT
/F1 22 Tf
50 720 Td
({organization}) Tj
/F1 16 Tf
0 -36 Td
({title}) Tj
/F1 12 Tf
0 -40 Td
(This certifies that) Tj
/F1 18 Tf
0 -28 Td
({recipient}) Tj
/F1 12 Tf
0 -30 Td
(has fulfilled all requirements and is awarded this credential with all honors.) Tj
0 -24 Td
({details}) Tj
0 -40 Td
(Authoritative Issuer ID: urn:zkdoc:issuer:acme-university) Tj
0 -20 Td
(Verification Standard: SHA-256 Digest + RSASSA-PSS RFC 8017) Tj
/F1 10 Tf
0 -50 Td
(*** {watermark_text} ***) Tj
0 -16 Td
(Notice: This document contains purely synthetic test data. It is not an official credential.) Tj
ET
"""
    stream_bytes = content_stream.encode('latin1')
    stream_len = len(stream_bytes)

    objects = []
    # 1: Catalog
    objects.append(b"1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
    # 2: Pages
    objects.append(b"2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")
    # 3: Page
    objects.append(b"3 0 obj\n<< /Type /Page /Parent 2 0 R /Resources << /Font << /F1 4 0 R >> >> /MediaBox [0 0 612 792] /Contents 5 0 R >>\nendobj\n")
    # 4: Font
    objects.append(b"4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n")
    # 5: Contents
    contents_header = f"5 0 obj\n<< /Length {stream_len} >>\nstream\n".encode('latin1')
    contents_footer = b"\nendstream\nendobj\n"
    objects.append(contents_header + stream_bytes + contents_footer)

    pdf_bytes = bytearray(b"%PDF-1.4\n")
    offsets = []
    for obj in objects:
        offsets.append(len(pdf_bytes))
        pdf_bytes.extend(obj)

    xref_offset = len(pdf_bytes)
    pdf_bytes.extend(b"xref\n0 6\n0000000000 65535 f \n")
    for offset in offsets:
        pdf_bytes.extend(f"{offset:010d} 00000 n \n".encode('latin1'))

    pdf_bytes.extend(f"trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n{xref_offset}\n%%EOF\n".encode('latin1'))
    return bytes(pdf_bytes)

def main():
    target_dir = os.path.dirname(os.path.abspath(__file__))

    # 1. Official synthetic university diploma
    doc1_bytes = create_simple_pdf(
        title="BACHELOR OF SCIENCE IN COMPUTER SCIENCE",
        recipient="Alice Chen",
        organization="ACME STATE UNIVERSITY - OFFICE OF THE REGISTRAR",
        details="Graduation Date: May 15, 2024 | Honors: Magna Cum Laude | Major GPA: 3.80 / 4.00",
        watermark_text="DEMO DOCUMENT - SYNTHETIC DATA - NOT AN OFFICIAL CERTIFICATE"
    )
    doc1_path = os.path.join(target_dir, "demo-degree-diploma.pdf")
    with open(doc1_path, "wb") as f:
        f.write(doc1_bytes)
    h1 = hashlib.sha256(doc1_bytes).hexdigest()
    print(f"Generated {doc1_path} ({len(doc1_bytes)} bytes, SHA-256: {h1})")

    # 2. Tampered diploma (single modification to test cryptographic detection)
    doc2_bytes = create_simple_pdf(
        title="BACHELOR OF SCIENCE IN COMPUTER SCIENCE",
        recipient="Alice Chen",
        organization="ACME STATE UNIVERSITY - OFFICE OF THE REGISTRAR",
        details="Graduation Date: May 15, 2024 | Honors: Summa Cum Laude | Major GPA: 4.00 / 4.00",
        watermark_text="TAMPERED DEMO DOCUMENT - SYNTHETIC DATA - INTEGRITY VIOLATION DEMO"
    )
    doc2_path = os.path.join(target_dir, "demo-degree-diploma-tampered.pdf")
    with open(doc2_path, "wb") as f:
        f.write(doc2_bytes)
    h2 = hashlib.sha256(doc2_bytes).hexdigest()
    print(f"Generated {doc2_path} ({len(doc2_bytes)} bytes, SHA-256: {h2})")

    # 3. Training certificate
    doc3_bytes = create_simple_pdf(
        title="CERTIFIED CLOUD SECURITY ARCHITECT",
        recipient="Alice Chen",
        organization="APEX GLOBAL CERTIFICATION AUTHORITY",
        details="Credential ID: APX-2024-88421 | Certification Date: June 1, 2024 | Score: 94%",
        watermark_text="DEMO DOCUMENT - SYNTHETIC DATA - NOT AN OFFICIAL CERTIFICATE"
    )
    doc3_path = os.path.join(target_dir, "demo-training-certificate.pdf")
    with open(doc3_path, "wb") as f:
        f.write(doc3_bytes)
    h3 = hashlib.sha256(doc3_bytes).hexdigest()
    print(f"Generated {doc3_path} ({len(doc3_bytes)} bytes, SHA-256: {h3})")

if __name__ == "__main__":
    main()
