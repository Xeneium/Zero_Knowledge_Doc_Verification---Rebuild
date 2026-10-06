# Zero-Knowledge Document Verification Platform
## Deterministic Canonicalization Specification (V1)

**Document Version:** 1.0.0  
**Status:** Approved Architectural Baseline  
**Phase:** Phase 0 — Architecture & Security Baseline  

---

### 1. Security Rationale & Threat Context

Digital signatures and cryptographic digests require bit-exact repeatability across platforms, operating systems, and JVM runtimes. Even a single-byte variation in whitespace, field order, number formatting, or character encoding invalidates cryptographic signatures.

Canonicalization is therefore a **critical cryptographic security boundary**. 

---

### 2. Prohibited Serialization Patterns

The following mechanisms are **strictly forbidden** from participating in signature generation, hashing, or verification:

| Prohibited Pattern | Failure Mode / Vulnerability |
| :--- | :--- |
| `Object.toString()` / `Map.toString()` | Non-standardized formatting, JVM vendor differences, non-deterministic map bucket order. |
| Java Native Serialization (`Serializable`) | Fragile bytecode coupling, Remote Code Execution (RCE) / deserialization gadget risks. |
| Unspecified JSON serialization (e.g. standard `ObjectMapper.writeValueAsString()`) | Non-deterministic field ordering, arbitrary whitespace, floating-point ambiguity. |
| Platform-Default Charset (`Charset.defaultCharset()`) | Encoding divergence between Windows (CP1252), Linux (UTF-8), and macOS. |
| Locale-Dependent Formatting (`String.format()`, `NumberFormat`) | Decimal separator variation (`, ` vs `.`), date format differences across regions. |
| Ambiguous Numeric Representation | Equating `100.0` with `100` or scientific notation `1e2`. |

---

### 3. Binary Canonical Encoding Specification (Format Version 1)

The platform adopts a deterministic, high-efficiency binary canonical encoding format (`zkdoc-canonical-v1`) designed specifically for verifiable credentials.

#### 3.1. Primitives & Endianness
* **Endianness:** All integers, counts, and lengths are encoded in **Network Byte Order (Big-Endian)**.
* **UUID:** Serialized as 16 raw bytes (most significant 64 bits followed by least significant 64 bits).
* **Timestamps:** Fixed 8-byte signed integer representing epoch milliseconds in UTC (`Instant.toEpochMilli()`).
* **Strings:** Length-prefixed string: 4-byte big-endian unsigned length $L$, followed by $L$ bytes of UTF-8 encoded NFC-normalized characters.
* **Integers:** Signed 8-byte big-endian integers (`long`).
* **Booleans:** Single byte: `0x01` for `true`, `0x00` for `false`.
* **Null Values:** Null attributes are explicitly prohibited in canonical credentials. Optional fields must either be omitted from the schema or carry a defined sentinel type.

#### 3.2. Lexicographical Attribute Sorting
* Credential attributes **must be sorted in strictly ascending lexicographical order** based on their UTF-8 byte representation of the `attributeName`.
* Duplicate attribute names are rejected during pre-validation.

#### 3.3. Binary Frame Layout for Credential

```
+-----------------------------------------------------------------------+
| Magic Bytes: 0x5A 0x4B 0x44 0x43 ("ZKDC")                             | (4 bytes)
+-----------------------------------------------------------------------+
| Canonical Format Version: 0x00 0x01 (Version 1)                       | (2 bytes)
+-----------------------------------------------------------------------+
| Credential UUID: Raw 128-bit identifier                               | (16 bytes)
+-----------------------------------------------------------------------+
| Schema URI Length: uint32                                             | (4 bytes)
+-----------------------------------------------------------------------+
| Schema URI: UTF-8 bytes                                               | (Variable)
+-----------------------------------------------------------------------+
| Issuer ID Length: uint32                                              | (4 bytes)
+-----------------------------------------------------------------------+
| Issuer ID: UTF-8 bytes                                                | (Variable)
+-----------------------------------------------------------------------+
| Issuer Key UUID: Raw 128-bit identifier                               | (16 bytes)
+-----------------------------------------------------------------------+
| IssuedAt: Epoch Millis int64                                          | (8 bytes)
+-----------------------------------------------------------------------+
| ExpiresAt: Epoch Millis int64                                         | (8 bytes)
+-----------------------------------------------------------------------+
| Attribute Count: uint32 (N)                                           | (4 bytes)
+-----------------------------------------------------------------------+
| FOR EACH ATTRIBUTE (1..N, Sorted by Name ASC):                        |
|   - Name Length: uint32                                               | (4 bytes)
|   - Name: UTF-8 bytes                                                 | (Variable)
|   - Type Tag: 0x01 (INTEGER), 0x02 (STRING), 0x03 (BOOLEAN)          | (1 byte)
|   - Value Payload:                                                    |
|       * If INTEGER: int64 big-endian                                  | (8 bytes)
|       * If STRING: Length uint32 + UTF-8 bytes                        | (Variable)
|       * If BOOLEAN: byte 0x00 or 0x01                                 | (1 byte)
+-----------------------------------------------------------------------+
```

---

### 4. Canonical Test Vector Requirements

Implementation suites must test against known static test vectors:
1. **Empty Attributes Vector:** Validates header, UUID, and zero count.
2. **Multi-Attribute Sorting Vector:** Validates that attributes entered out-of-order (`{"z": 1, "a": 2}`) serialize identically to sorted order (`{"a": 2, "z": 1}`).
3. **Unicode Normalization Vector:** Validates that decomposed characters (e.g., `e` + `´`) and precomposed characters (`é`) serialize to identical NFC binary representations.
4. **Boundary Integers Vector:** Validates signed 64-bit bounds ($0$, $2^{63}-1$, min/max values).
