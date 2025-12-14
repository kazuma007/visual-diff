package com.visualdiff.models

import upickle.default.ReadWriter

enum DiffType(val name: String) derives ReadWriter:

  case Added extends DiffType("added")

  case Removed extends DiffType("removed")

  case Changed extends DiffType("changed")
