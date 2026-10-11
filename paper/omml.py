"""Minimal OMML (Office Math) builder for python-docx.

Each helper returns an XML fragment string; `math()` wraps fragments in <m:oMath>.
"""
from xml.sax.saxutils import escape

M_NS = "http://schemas.openxmlformats.org/officeDocument/2006/math"
W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
FONT = '<w:rPr><w:rFonts w:ascii="Cambria Math" w:hAnsi="Cambria Math"/><w:sz w:val="20"/></w:rPr>'


def r(text, roman=False):
    sty = '<m:rPr><m:sty m:val="p"/></m:rPr>' if roman else ""
    return f'<m:r>{sty}{FONT}<m:t xml:space="preserve">{escape(text)}</m:t></m:r>'


def v(text):          # italic variable
    return r(text)


def t(text):          # upright text / operators / numbers
    return r(text, roman=True)


def cat(*parts):
    return "".join(parts)


def sub(base, s):
    return f"<m:sSub><m:e>{base}</m:e><m:sub>{s}</m:sub></m:sSub>"


def sup(base, s):
    return f"<m:sSup><m:e>{base}</m:e><m:sup>{s}</m:sup></m:sSup>"


def subsup(base, s, p):
    return f"<m:sSubSup><m:e>{base}</m:e><m:sub>{s}</m:sub><m:sup>{p}</m:sup></m:sSubSup>"


def frac(num, den):
    return f"<m:f><m:num>{num}</m:num><m:den>{den}</m:den></m:f>"


def delim(content, beg="(", end=")"):
    return (f'<m:d><m:dPr><m:begChr m:val="{escape(beg)}"/><m:endChr m:val="{escape(end)}"/></m:dPr>'
            f"<m:e>{content}</m:e></m:d>")


def nary(chr_, lower, upper, body):
    lim = "" if upper is not None else '<m:supHide m:val="1"/>'
    up = upper if upper is not None else ""
    return (f'<m:nary><m:naryPr><m:chr m:val="{chr_}"/><m:limLoc m:val="undOvr"/>{lim}</m:naryPr>'
            f"<m:sub>{lower}</m:sub><m:sup>{up}</m:sup><m:e>{body}</m:e></m:nary>")


def sqrt(content):
    return f'<m:rad><m:radPr><m:degHide m:val="1"/></m:radPr><m:deg/><m:e>{content}</m:e></m:rad>'


def func(name, arg):
    return f"<m:func><m:fName>{t(name)}</m:fName><m:e>{arg}</m:e></m:func>"


def bar(content):
    return f'<m:bar><m:barPr><m:pos m:val="top"/></m:barPr><m:e>{content}</m:e></m:bar>'


def lim_low(base, low):
    return f"<m:limLow><m:e>{base}</m:e><m:lim>{low}</m:lim></m:limLow>"


def math(*parts):
    return f'<m:oMath xmlns:m="{M_NS}" xmlns:w="{W_NS}">{"".join(parts)}</m:oMath>'
