#!/usr/bin/env python3
"""Synthetic Mac journal parser tests: no original text/pictures in fixtures."""
import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location("journal",Path(__file__).with_name("prepare-journal.py"))
journal=importlib.util.module_from_spec(spec);spec.loader.exec_module(journal)


def roman(n):
    out=""
    for size,symbol in ((100,"C"),(90,"XC"),(50,"L"),(40,"XL"),(10,"X"),(9,"IX"),(5,"V"),(4,"IV"),(1,"I")):
        while n>=size:out+=symbol;n-=size
    return out


def documents():
    first="\n".join("Proclamation "+roman(n)+"\nInvented proclamation." for n in journal.PROCLAMATIONS)
    first+="\nJOURNAL ENTRIES\n"
    for n in range(1,34):first+=f"Journal Entry {n}:\nInvented entry {n}.\n"
    first+="+ OLD MAC MENU INSTRUCTIONS +\n"
    second="Continuation of entry 33.\n"
    for n in range(34,59):second+=f"Journal Entry {n}:\nInvented entry {n}.\n"
    second+="TAVERN TALES\nInstructions are not an entry.\n"
    for n in range(1,24):second+=f"Tale {n}: Invented tale {n}.\n"
    return [(first,{}),(second,{})]


class JournalTest(unittest.TestCase):
    def test_full_set_and_split_entry(self):
        parsed=journal.parse_documents(documents())
        self.assertEqual(99,len(parsed))
        self.assertIn("Continuation of entry 33.",parsed[0,33][0][1])
        self.assertNotIn("MENU",parsed[0,33][0][1])
        self.assertNotIn("TAVERN",parsed[0,58][0][1])
        self.assertEqual("Invented tale 1.",parsed[2,1][0][1])
    def test_image_only_entry_and_order(self):
        docs=documents();text,_=docs[0]
        text=text.replace("Journal Entry 15:\nInvented entry 15.", '<<"[GRAPHIC: ENTRY 15]">>\n\xa0')
        docs[0]=(text,{1000:b"synthetic PNG"})
        blocks=journal.parse_documents(docs)[0,15]
        self.assertEqual((1,b"synthetic PNG"),blocks[1])
    def test_atlas_without_text_heading(self):
        docs=documents();text,_=docs[1]
        text=text.replace("Journal Entry 37:\nInvented entry 37.", '<<"[GRAPHIC: A MASSIVE ATLAS">>\n\xa0\n<<"[GRAPHIC: MAP OF PHLAN">>\n\xa0')
        docs[1]=(text,{1000:b"first",1001:b"second"})
        blocks=journal.parse_documents(docs)[0,37]
        self.assertEqual([b"first",b"second"],[v for k,v in blocks if k])
    def test_missing_duplicate_or_unused_content_fails(self):
        for before,after in (("Journal Entry 2:","Journal Entry 1:"),("Journal Entry 2:","Missing heading:")):
            docs=documents();docs[0]=(docs[0][0].replace(before,after),{})
            with self.assertRaises(ValueError):journal.parse_documents(docs)
        docs=documents();docs[0]=(docs[0][0],{1000:b"unused"})
        with self.assertRaises(ValueError):journal.parse_documents(docs)
    def test_binary_format_and_roman_numbers(self):
        for n in journal.PROCLAMATIONS:self.assertEqual(n,journal.roman(roman(n)))
        data=journal.encode(journal.parse_documents(documents()))
        self.assertTrue(data.startswith(b"PRJR\1"))
        self.assertIn(b"Continuation of entry 33.",data)


if __name__=="__main__":unittest.main()
