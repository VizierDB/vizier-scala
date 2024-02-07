/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
package info.vizierdb.artifacts

import play.api.libs.json._

sealed trait VegaAutosize

object VegaAutosize
{
  case object NoPadding extends VegaAutosize
  case object Pad extends VegaAutosize
  case object Fit extends VegaAutosize
  case object FitX extends VegaAutosize
  case object FitY extends VegaAutosize

  implicit val format: Format[VegaAutosize] = Format(
    new Reads[VegaAutosize]{
      def reads(j: JsValue) =
        j.as[String].toLowerCase match {
          case "none"  => JsSuccess(NoPadding)
          case "pad"   => JsSuccess(Pad)
          case "fit"   => JsSuccess(Fit)
          case "fit-x" => JsSuccess(FitX)
          case "fit-y" => JsSuccess(FitY)
          case _ => JsError()
        }
    },
    new Writes[VegaAutosize]{
      def writes(j: VegaAutosize) =
        JsString(j match {
          case NoPadding => "none" 
          case Pad       => "pad"  
          case Fit       => "fit"  
          case FitX      => "fit-x"
          case FitY      => "fit-y"
        })
    }
  )
}

/**
 * A standard css color string.  Examples include:
 * * A standard hexadecimal code (e.g., '#f304d3') 
 * * A multipart encoding: (e.g., 'rgb(253, 12, 134)')
 * * A standard color code (e.g., 'steelblue')
 */
case class VegaColor(html:String) 
{
  def this(r: Byte, g: Byte, b: Byte)
  {
    this("#%2x%2x%2x".format(r, g, b))
  }
}
object VegaColor
{
  def apply(r: Byte, g: Byte, b: Byte): VegaColor = new VegaColor(r, g, b)

  implicit val format = Format[VegaColor](
    new Reads[VegaColor]{
      def reads(j: JsValue) = JsSuccess(VegaColor(j.as[String]))
    },
    new Writes[VegaColor]{
      def writes(j: VegaColor) = JsString(j.html)
    }
  )
}

case class VegaPadding(left: Int, top: Int, right: Int, bottom: Int)
{
  def this(all: Int) = this(all, all, all, all)
}
object VegaPadding
{
  val defaultReads: Reads[VegaPadding] = Json.reads
  def all(x: Int) = new VegaPadding(x)
  
  implicit val format = Format[VegaPadding](
    new Reads[VegaPadding]{
      def reads(j: JsValue) =
        j match {
          case JsNumber(n) => JsSuccess(new VegaPadding(n.toInt))
          case o:JsObject => defaultReads.reads(o)
          case _ => JsError()
        }
    },
    Json.writes
  )
}

sealed trait VegaFileType
object VegaFileType
{
  /**
   * A JSON array of tuple objects
   */
  case object JSON extends VegaFileType
  /**
   * A comma-separated value file
   */
  case object CSV extends VegaFileType
  /**
   * A tab-separated value file
   */
  case object TSV extends VegaFileType
  /**
   * A delimited text file
   */
  case object DSV extends VegaFileType
  /**
   * Topographical json data
   */
  case object TOPOJSON extends VegaFileType

  implicit val format = Format[VegaFileType](
    new Reads[VegaFileType]{
      def reads(j: JsValue) = 
        j.as[String].toLowerCase match {
          case "json"     => JsSuccess(JSON)
          case "csv"      => JsSuccess(CSV)
          case "tsv"      => JsSuccess(TSV)
          case "dsv"      => JsSuccess(DSV)
          case "topojson" => JsSuccess(TOPOJSON)
          case _ => JsError()
        }
    },
    new Writes[VegaFileType]{
      def writes(j: VegaFileType) =
        JsString(j match {
          case JSON => "json"
          case CSV => "csv"
          case TSV => "tsv"
          case DSV => "dsv"
          case TOPOJSON => "topojson"
        })
    }
  )
}

sealed trait VegaParser
object VegaParser
{
  implicit val format = Format[VegaParser](
    new Reads[VegaParser]{
      def reads(j: JsValue): JsResult[VegaParser] = 
        j match {
          case _:JsString => VegaDataType.format.reads(j)
          case _:JsObject => JsSuccess(VegaSchema(j.as[Map[String, VegaDataType]]))
          case _ => JsError()
        }
    },
    new Writes[VegaParser]{
      def writes(j: VegaParser) =
        j match {
          case v: VegaDataType => VegaDataType.format.writes(v)
          case v: VegaSchema   => Json.toJson(v.fields)
        }
    }
  )
}

sealed trait VegaDataType extends VegaParser
object VegaDataType
{
  case object Boolean extends VegaDataType
  case object Date extends VegaDataType
  case object Number extends VegaDataType
  case object String extends VegaDataType

  implicit val format = Format[VegaDataType](
    new Reads[VegaDataType]{
      def reads(j: JsValue) = 
        j.as[String].toLowerCase match {
          case "boolean" => JsSuccess(Boolean)
          case "date" => JsSuccess(Date)
          case "number" => JsSuccess(Number)
          case "string" => JsSuccess(String)
          case _ => JsError()
        }
    },
    new Writes[VegaDataType]{
      def writes(j: VegaDataType) =
        JsString(j match {
          case Boolean => "boolean"
          case Date => "date"
          case Number => "number"
          case String => "string"
        })
    }
  )
}

case class VegaSchema(fields: Map[String, VegaDataType]) extends VegaParser
object VegaSchema
{
  implicit val format = Format[VegaSchema](
    new Reads[VegaSchema]{
      def reads(j: JsValue) = 
        JsSuccess(VegaSchema(j.as[Map[String, VegaDataType]]))
    },
    new Writes[VegaSchema]{
      def writes(j: VegaSchema) =
        Json.toJson(j.fields)
    }
  )
}


case class VegaFileFormat(
  `type`: VegaFileType = VegaFileType.JSON,
  parse: VegaSchema
)
object VegaFileFormat
{
  implicit val format: Format[VegaFileFormat] = Json.format
}


sealed trait VegaRegressionMethod
{
  def key: String
}
object VegaRegressionMethod
{
  case object Linear extends VegaRegressionMethod
  {
    def key = "linear"
  }
  case object Logarithmic extends VegaRegressionMethod
  {
    def key = "log"
  }
  case object Exponential extends VegaRegressionMethod
  {
    def key = "exp"
  }
  case object Power extends VegaRegressionMethod
  {
    def key = "pow"
  }
  case object Quadratic extends VegaRegressionMethod
  {
    def key = "qua"
  }

  val all = Seq(
    Linear,
    Logarithmic,
    Exponential,
    Power,
    Quadratic
  )
  val byKey =
    all.map { x => x.key -> x }.toMap

  def apply(name: String): Option[VegaRegressionMethod] = 
    byKey.get(name)

  implicit val format = Format[VegaRegressionMethod](
    new Reads[VegaRegressionMethod] {
      def reads(j: JsValue): JsResult[VegaRegressionMethod] = 
        byKey.get(j.as[String]).map { JsSuccess (_) }.getOrElse { JsError() } 
    },
    new Writes[VegaRegressionMethod] {
      def writes(j: VegaRegressionMethod): JsValue =
        JsString(j.key)
    }
  ) 
}



sealed trait VegaTransform

object VegaTransform
{

  case class Regression(
    x:String,
    y:String,
    method: VegaRegressionMethod,
    `type`: String = "regression"
  ) extends VegaTransform
  
  implicit val regressionFormat: Format[Regression] = Json.format
  implicit val format = Format[VegaTransform](
    new Reads[VegaTransform]{ 
      def reads(j: JsValue): JsResult[VegaTransform] =
        (j \ "type").as[String] match {
          case "regression" => JsSuccess(j.as[Regression])
          case _ => JsError()
        }
    },
    new Writes[VegaTransform]{
      def writes(j: VegaTransform): JsValue =
        j match {
          case r:Regression => Json.toJson(r)
        }
    }
  )
}

/**
 * A raw Vega Dataset
 * 
 * One of [source], [url], [values] must be specified.
 * * source: Load data by combining source datasets
 * * url: Load data from the specified url ([format] must be specified)
 * * values: A sequence of tuples specified inline
 * 
 * Triggers (`on`) and transformation pipelines (`transform`) are not
 * supported yet
 */
case class VegaData(
  name: String,
  format: Option[VegaFileFormat] = None,
  source: Option[Seq[String]] = None,
  url: Option[String] = None,
  values: Option[Seq[JsObject]] = None,
  async: Boolean = false,
  // on: Seq[VegaTrigger]
  transform: Option[Seq[VegaTransform]] = None
)
object VegaData
{
  def byValues(name: String, values: Seq[JsObject]) =
    VegaData(name = name, values = Some(values))

  implicit val format: Format[VegaData] = Json.format
}



/**
 * A vega scale type
 */
sealed trait VegaScaleType
object VegaScaleType
{
  sealed trait Quantitiative extends VegaScaleType
  case object Linear extends Quantitiative
  case object Log extends Quantitiative
  case object Pow extends Quantitiative
  case object Sqrt extends Quantitiative
  case object Symlog extends Quantitiative
  case object Time extends Quantitiative
  case object UTC extends Quantitiative
  case object Sequential extends Quantitiative

  sealed trait Discrete extends VegaScaleType
  case object Ordinal extends Discrete
  case object Band extends Discrete
  case object Point extends Discrete

  sealed trait Discretizing extends VegaScaleType
  case object Quantile extends Discretizing
  case object Quantize extends Discretizing
  case object Threshold extends Discretizing
  case object BinOrdinal extends Discretizing

  implicit val format = Format[VegaScaleType](
    new Reads[VegaScaleType]{
      def reads(j: JsValue) = 
        j.as[String].toLowerCase match {
          case "linear" => JsSuccess(Linear)
          case "log" => JsSuccess(Log)
          case "pow" => JsSuccess(Pow)
          case "sqrt" => JsSuccess(Sqrt)
          case "symlog" => JsSuccess(Symlog)
          case "time" => JsSuccess(Time)
          case "utc" => JsSuccess(UTC)
          case "sequential" => JsSuccess(Sequential)
          case "ordinal" => JsSuccess(Ordinal)
          case "band" => JsSuccess(Band)
          case "point" => JsSuccess(Point)
          case "quantile" => JsSuccess(Quantile)
          case "quantize" => JsSuccess(Quantize)
          case "threshold" => JsSuccess(Threshold)
          case "bin-ordinal" => JsSuccess(BinOrdinal)
          case _ => JsError()
        }
    },
    new Writes[VegaScaleType]{
      def writes(j: VegaScaleType) =
        JsString(j match {
          case Linear => "linear"
          case Log => "log"
          case Pow => "pow"
          case Sqrt => "sqrt"
          case Symlog => "symlog"
          case Time => "time"
          case UTC => "utc"
          case Sequential => "sequential"
          case Ordinal => "ordinal"
          case Band => "band"
          case Point => "point"
          case Quantile => "quantile"
          case Quantize => "quantize"
          case Threshold => "threshold"
          case BinOrdinal => "bin-ordinal"
        })
    }
  )
}

sealed trait VegaInterpolationMethod
object VegaInterpolationMethod
{
  case object RGB extends VegaInterpolationMethod
  case object HSL extends VegaInterpolationMethod
  case object HSLLong extends VegaInterpolationMethod
  case object Lab extends VegaInterpolationMethod
  case object HCL extends VegaInterpolationMethod
  case object HCLLong extends VegaInterpolationMethod
  case object CubeHelix extends VegaInterpolationMethod
  case object CubeHelixLong extends VegaInterpolationMethod

  implicit val format = Format[VegaInterpolationMethod](
    new Reads[VegaInterpolationMethod]{
      def reads(j: JsValue) =
        j.as[String].toLowerCase match {
          case "rgb" => JsSuccess(RGB)
          case "hsl" => JsSuccess(HSL)
          case "hsl-long" => JsSuccess(HSLLong)
          case "lab" => JsSuccess(Lab)
          case "hcl" => JsSuccess(HCL)
          case "hcl-long" => JsSuccess(HCLLong)
          case "cubehelix" => JsSuccess(CubeHelix)
          case "cubehelix-long" => JsSuccess(CubeHelixLong)
          case _ => JsError()
        }
    },
    new Writes[VegaInterpolationMethod]{
      def writes(j: VegaInterpolationMethod) =
        JsString(j match {
          case RGB => "rgb"
          case HSL => "hsl"
          case HSLLong => "hsl-long"
          case Lab => "lab"
          case HCL => "hcl"
          case HCLLong => "hcl-long"
          case CubeHelix => "cubehelix"
          case CubeHelixLong => "cubehelix-long"
        })
    }
  )
}

sealed trait VegaDomain
object VegaDomain
{
  case class Literal(values: Seq[JsValue]) extends VegaDomain
  case class Data(field: String, data: String) extends VegaDomain

  implicit val dataFormat: Format[Data] = Json.format

  implicit val format = Format[VegaDomain](
    new Reads[VegaDomain] {
      def reads(j: JsValue) =
        j match {
          case a:JsArray => JsSuccess(Literal(a.value))
          case o:JsObject => dataFormat.reads(o)
          case _ => JsError()
        }
    },
    new Writes[VegaDomain] {
      def writes(j: VegaDomain) =
        j match {
          case Literal(values) => Json.toJson(values)
          case d:Data => dataFormat.writes(d)
        }
    }
  )
}

sealed trait VegaRange
object VegaRange
{
  case class Literal(values: Seq[JsValue]) extends VegaRange
  case class Data(field: String, data: String) extends VegaRange
  case object Width extends VegaRange
  case object Height extends VegaRange
  case object Symbol extends VegaRange
  case object Category extends VegaRange
  case object Diverging extends VegaRange
  case object Ordinal extends VegaRange
  case object Ramp extends VegaRange
  case object Heatmap extends VegaRange

  implicit val dataFormat: Format[Data] = Json.format

  implicit val format = Format[VegaRange](
    new Reads[VegaRange] {
      def reads(j: JsValue) =
        j match {
          case JsString(x) if x.equalsIgnoreCase("width") => JsSuccess(Width)
          case JsString(x) if x.equalsIgnoreCase("height") => JsSuccess(Height)
          case JsString(x) if x.equalsIgnoreCase("symbol") => JsSuccess(Symbol)
          case JsString(x) if x.equalsIgnoreCase("category") => JsSuccess(Category)
          case JsString(x) if x.equalsIgnoreCase("diverging") => JsSuccess(Diverging)
          case JsString(x) if x.equalsIgnoreCase("ordinal") => JsSuccess(Ordinal)
          case JsString(x) if x.equalsIgnoreCase("ramp") => JsSuccess(Ramp)
          case JsString(x) if x.equalsIgnoreCase("heatmap") => JsSuccess(Heatmap)
          case a:JsArray => JsSuccess(Literal(a.value))
          case o:JsObject => dataFormat.reads(o)
          case _ => JsError()
        }
    },
    new Writes[VegaRange] {
      def writes(j: VegaRange) =
        j match {
          case Literal(values) => Json.toJson(values)
          case d:Data => dataFormat.writes(d)
          case Width => JsString("width")
          case Height => JsString("height")
          case Symbol => JsString("symbol")
          case Category => JsString("category")
          case Diverging => JsString("diverging")
          case Ordinal => JsString("ordinal")
          case Ramp => JsString("ramp")
          case Heatmap => JsString("heatmap")
        }
    }
  )

}

/**
 * A mapping from data values (numbers, dates, categories) to 
 * visual values (pixels, colors, sizes).  
 * 
 * See: https://vega.github.io/vega/docs/scales/
 * 
 * At the moment, the 'range' and 'domain' are implemented with only 
 * limited functionality.
 * 
 * Specifically, both only presently support the "array of discrete
 * values" variant.  Supporting other variants should be doable with
 * a bit more effort, but that's a TODO for later.
 */
case class VegaScale(
  name: String,
  `type`: VegaScaleType,
  range: Option[VegaRange] = None,
  domain: Option[VegaDomain] = None,
  interpolate: Option[VegaInterpolationMethod] = None,
  reverse: Option[Boolean] = None,
  round:   Option[Boolean] = None,
  domainRaw: Option[Seq[Double]] = None,
  domainMax: Option[Double] = None,
  domainMin: Option[Double] = None,
  domainMid: Option[Double] = None,
  padding: Option[Double] = None,
)
object VegaScale
{
  implicit val format: Format[VegaScale] = Json.format
}

sealed trait VegaOrientation
object VegaOrientation
{
  case object Left extends VegaOrientation
  case object Right extends VegaOrientation
  case object Top extends VegaOrientation
  case object Bottom extends VegaOrientation
  implicit val format = Format[VegaOrientation](
    new Reads[VegaOrientation]{
      def reads(j: JsValue) = 
        j.as[String].toLowerCase match {
          case "left" => JsSuccess(Left)
          case "right" => JsSuccess(Right)
          case "top" => JsSuccess(Top)
          case "bottom" => JsSuccess(Bottom)
          case _ => JsError()
        }
    },
    new Writes[VegaOrientation]{
      def writes(j: VegaOrientation) =
        JsString(j match {
          case Left => "left"
          case Right => "right"
          case Top => "top"
          case Bottom => "bottom"
        })
    }
  )
}

sealed trait VegaCapType
object VegaCapType
{
  case object Butt extends VegaCapType
  case object Round extends VegaCapType
  case object Square extends VegaCapType

  implicit val format = Format[VegaCapType](
    new Reads[VegaCapType]{
      def reads(j: JsValue) =
        j.as[String].toLowerCase match{
          case "butt" => JsSuccess(Butt)
          case "round" => JsSuccess(Round)
          case "square" => JsSuccess(Square)
          case _ => JsError()
        }
    },
    new Writes[VegaCapType]{
      def writes(j: VegaCapType) =
        JsString(j match {
          case Butt => "butt"
          case Round => "round"
          case Square => "square"
        })
    }
  )
}

sealed trait VegaAxisFormat
object VegaAxisFormat
{
  case object Number extends VegaAxisFormat
  case object Time extends VegaAxisFormat
  case object UTC extends VegaAxisFormat

  implicit val format = Format[VegaAxisFormat](
    new Reads[VegaAxisFormat]{
      def reads(j: JsValue) =
        j.as[String].toLowerCase match{
          case "number" => JsSuccess(Number)
          case "time" => JsSuccess(Time)
          case "utc" => JsSuccess(UTC)
          case _ => JsError()
        }
    },
    new Writes[VegaAxisFormat]{
      def writes(j: VegaAxisFormat) =
        JsString(j match {
          case Number => "number"
          case Time => "time"
          case UTC => "utc"
        })
    }
  )
}

sealed trait VegaBaseline
object VegaBaseline
{
  case object Alphabetic extends VegaBaseline
  case object Top extends VegaBaseline
  case object Middle extends VegaBaseline
  case object Bottom extends VegaBaseline
  case object LineTop extends VegaBaseline
  case object LineBottom extends VegaBaseline

  implicit val format = Format[VegaBaseline](
    new Reads[VegaBaseline]{
      def reads(j: JsValue) =
        j.as[String].toLowerCase match{
          case "alphabetic" => JsSuccess(Alphabetic)
          case "top" => JsSuccess(Top)
          case "middle" => JsSuccess(Middle)
          case "bottom" => JsSuccess(Bottom)
          case "line-top" => JsSuccess(LineTop)
          case "line-bottom" => JsSuccess(LineBottom)
          case _ => JsError()
        }
    },
    new Writes[VegaBaseline]{
      def writes(j: VegaBaseline) =
        JsString(j match {
          case Alphabetic => "alphabetic"
          case Top => "top"
          case Middle => "middle"
          case Bottom => "bottom"
          case LineTop => "line-top"
          case LineBottom => "line-bottom"
        })
    }
  )
}

sealed trait VegaTickBandStyle
object VegaTickBandStyle
{
  case object Center extends VegaTickBandStyle
  case object Extent extends VegaTickBandStyle

  implicit val format = Format[VegaTickBandStyle](
    new Reads[VegaTickBandStyle]{
      def reads(j: JsValue) =
        j.as[String].toLowerCase match{
          case "center" => JsSuccess(Center)
          case "extent" => JsSuccess(Extent)
          case _ => JsError()
        }
    },
    new Writes[VegaTickBandStyle]{
      def writes(j: VegaTickBandStyle) =
        JsString(j match {
          case Center => "center"
          case Extent => "extent"
        })
    }
  )
}

sealed trait VegaAnchorPoint
object VegaAnchorPoint
{
  case object Start extends VegaAnchorPoint
  case object Middle extends VegaAnchorPoint
  case object End extends VegaAnchorPoint
  case object Default extends VegaAnchorPoint

  implicit val format = Format[VegaAnchorPoint](
    new Reads[VegaAnchorPoint]{
      def reads(j: JsValue) =
        j match {
          case JsString(s) if s.equalsIgnoreCase("start") => JsSuccess(Start)
          case JsString(s) if s.equalsIgnoreCase("middle") => JsSuccess(Middle)
          case JsString(s) if s.equalsIgnoreCase("end") => JsSuccess(End)
          case JsNull => JsSuccess(Default)
          case _ => JsError()
        }
    },
    new Writes[VegaAnchorPoint]{
      def writes(j: VegaAnchorPoint) =
        j match {
          case Start => JsString("start")
          case Middle => JsString("middle")
          case End => JsString("end")
          case Default => JsNull
        }
    }
  )
}

sealed trait VegaAlign
object VegaAlign
{
  case object Left extends VegaAlign
  case object Middle extends VegaAlign
  case object Right extends VegaAlign

  implicit val format = Format[VegaAlign](
    new Reads[VegaAlign]{
      def reads(j: JsValue) =
        j.as[String].toLowerCase match{
          case "left" => JsSuccess(Left)
          case "middle" => JsSuccess(Middle)
          case "right" => JsSuccess(Right)
          case _ => JsError()
        }
    },
    new Writes[VegaAlign]{
      def writes(j: VegaAlign) =
        JsString(j match {
          case Left => "left"
          case Middle => "middle"
          case Right => "right"
        })
    }
  )
}

/**
 * A visualization of spatial scale mappings using ticks, gridlines, 
 * and labels.  
 * 
 * See: https://vega.github.io/vega/docs/axes/
 * 
 * Not all features are fully supported.  Remaining features are
 * a TODO:
 * * 'format' doesn't support TimeMultiFormat yet
 * * 'labelBound' and 'labelFlush' don't support the Number option
 * * 'labelFontWeight', 'titleFontWeight' don't support the Number option
 * * 'labelOverlap' only supports the boolean option
 * * 'minExtent', 'maxExtent', 'offset', and 'position' only support the 
 *   Number option
 * * 'tickCount' only supports the Number option
 * * 'title' only supports the single-line variant
 * * 'titlePadding' only supports the Number option
 */ 
case class VegaAxis(
  scale: String,
  orient: VegaOrientation,
  bandPosition: Option[Double]         = None,
  domain: Option[Boolean]              = None,
  domainCap: Option[VegaCapType]       = None,
  domainColor: Option[VegaColor]       = None,
  domainDash: Option[Seq[Double]]      = None,
  domainDashOffset: Option[Double]     = None,
  domainOpacity: Option[Double]        = None,
  domainWidth: Option[Double]          = None,
  format: Option[String]               = None,
  formatType: Option[VegaAxisFormat]   = None,
  grid: Option[Boolean]                = None,
  gridCap: Option[VegaCapType]         = None,
  gridColor: Option[VegaColor]         = None,
  gridDash: Option[Seq[Double]]        = None,
  gridDashOffset: Option[Double]       = None,
  gridScale: Option[String]            = None,
  gridWidth: Option[Double]            = None,
  labels: Option[Boolean]              = None,
  labelAlign: Option[String]           = None,
  labelAngle: Option[Double]           = None,
  labelBaseline: Option[VegaBaseline]  = None,
  labelBound: Option[Boolean]          = None,
  labelColor: Option[VegaColor]        = None,
  labelFlush: Option[Boolean]          = None,
  labelFlushOffset: Option[Double]     = None,
  labelFont: Option[String]            = None,
  labelFontSize: Option[Double]        = None,
  labelFontStyle: Option[String]       = None,
  labelFontWeight: Option[String]      = None,
  labelLimit: Option[Int]              = None,
  labelLineHeight: Option[Double]      = None,
  labelOffset: Option[Double]          = None,
  labelOpacity: Option[Double]         = None,
  labelOverlap: Option[Boolean]        = None,
  labelPadding: Option[Double]         = None,
  labelSeparation: Option[Double]      = None,
  minExtent: Option[Double]            = None,
  maxExtent: Option[Double]            = None,
  offset: Option[Double]               = None,
  position: Option[Double]             = None,
  ticks: Option[Boolean]               = None,
  tickBand: Option[VegaTickBandStyle]  = None,
  tickCap: Option[VegaCapType]         = None,
  tickColor: Option[VegaColor]         = None,
  tickCount: Option[Int]               = None,
  tickDash: Option[Seq[Double]]        = None,
  tickDashOffset: Option[Double]       = None,
  tickMinStep: Option[Double]          = None,
  tickExtra: Option[Boolean]           = None,
  tickOffset: Option[Double]           = None,
  tickOpacity: Option[Double]          = None,
  tickRound: Option[Boolean]           = None,
  tickSize: Option[Double]             = None,
  tickWidth: Option[Double]            = None,
  title: Option[String]                = None,
  titleAnchor: Option[VegaAnchorPoint] = None,
  titleAlign: Option[VegaAlign]        = None,
  titleAngle: Option[Double]           = None,
  titleBaseline: Option[VegaBaseline]  = None,
  titleColor: Option[VegaColor]        = None,
  titleFont: Option[String]            = None,
  titleFontSize: Option[Double]        = None,
  titleFontStyle: Option[String]       = None,
  titleFontWeight: Option[String]      = None,
  titleLimit: Option[Double]           = None,
  titleLineHeight: Option[Double]      = None,
  titleOpacity: Option[Double]         = None,
  titlePadding: Option[Double]         = None,
  titleX: Option[Double]               = None,
  titleY: Option[Double]               = None,
  translate: Option[Double]            = None,
  values: Option[Seq[Double]]           = None,
  zindex: Option[Int]                  = None
)
object VegaAxis
{

  // In principle, we should be able to use Json.format to generate 
  // the following codec automatically... unfortunately, there's
  // a bajillion fields in this type, and quite a few more than 
  // Json.format is set up to support.  We need to manually make
  // a format for it. 😥
  implicit val format: Format[VegaAxis] = Format[VegaAxis](
    new Reads[VegaAxis]{
      def reads(j: JsValue) =
        JsSuccess(VegaAxis(
          scale  = (j \ "scale").as[String],
          orient = (j \ "orient").as[VegaOrientation],
          bandPosition = (j \ "bandPosition").asOpt[Double],
          domain = (j \ "domain").asOpt[Boolean],
          domainCap = (j \ "domainCap").asOpt[VegaCapType],
          domainColor = (j \ "domainColor").asOpt[VegaColor],
          domainDash = (j \ "domainDash").asOpt[Seq[Double]],
          domainDashOffset = (j \ "domainDashOffset").asOpt[Double],
          domainOpacity = (j \ "domainOpacity").asOpt[Double],
          domainWidth = (j \ "domainWidth").asOpt[Double],
          `format` = (j \ "format").asOpt[String],
          formatType = (j \ "formatType").asOpt[VegaAxisFormat],
          grid = (j \ "grid").asOpt[Boolean],
          gridCap = (j \ "gridCap").asOpt[VegaCapType],
          gridColor = (j \ "gridColor").asOpt[VegaColor],
          gridDash = (j \ "gridDash").asOpt[Seq[Double]],
          gridDashOffset = (j \ "gridDashOffset").asOpt[Double],
          gridScale = (j \ "gridScale").asOpt[String],
          gridWidth = (j \ "gridWidth").asOpt[Double],
          labels = (j \ "labels").asOpt[Boolean],
          labelAlign = (j \ "labelAlign").asOpt[String],
          labelAngle = (j \ "labelAngle").asOpt[Double],
          labelBaseline = (j \ "labelBaseline").asOpt[VegaBaseline],
          labelBound = (j \ "labelBound").asOpt[Boolean],
          labelColor = (j \ "labelColor").asOpt[VegaColor],
          labelFlush = (j \ "labelFlush").asOpt[Boolean],
          labelFlushOffset = (j \ "labelFlushOffset").asOpt[Double],
          labelFont = (j \ "labelFont").asOpt[String],
          labelFontSize = (j \ "labelFontSize").asOpt[Double],
          labelFontStyle = (j \ "labelFontStyle").asOpt[String],
          labelFontWeight = (j \ "labelFontWeight").asOpt[String],
          labelLimit = (j \ "labelLimit").asOpt[Int],
          labelLineHeight = (j \ "labelLineHeight").asOpt[Double],
          labelOffset = (j \ "labelOffset").asOpt[Double],
          labelOpacity = (j \ "labelOpacity").asOpt[Double],
          labelOverlap = (j \ "labelOverlap").asOpt[Boolean],
          labelPadding = (j \ "labelPadding").asOpt[Double],
          labelSeparation = (j \ "labelSeparation").asOpt[Double],
          minExtent = (j \ "minExtent").asOpt[Double],
          maxExtent = (j \ "maxExtent").asOpt[Double],
          offset = (j \ "offset").asOpt[Double],
          position = (j \ "position").asOpt[Double],
          ticks = (j \ "ticks").asOpt[Boolean],
          tickBand = (j \ "tickBand").asOpt[VegaTickBandStyle],
          tickCap = (j \ "tickCap").asOpt[VegaCapType],
          tickColor = (j \ "tickColor").asOpt[VegaColor],
          tickCount = (j \ "tickCount").asOpt[Int],
          tickDash = (j \ "tickDash").asOpt[Seq[Double]],
          tickDashOffset = (j \ "tickDashOffset").asOpt[Double],
          tickMinStep = (j \ "tickMinStep").asOpt[Double],
          tickExtra = (j \ "tickExtra").asOpt[Boolean],
          tickOffset = (j \ "tickOffset").asOpt[Double],
          tickOpacity = (j \ "tickOpacity").asOpt[Double],
          tickRound = (j \ "tickRound").asOpt[Boolean],
          tickSize = (j \ "tickSize").asOpt[Double],
          tickWidth = (j \ "tickWidth").asOpt[Double],
          title = (j \ "title").asOpt[String],
          titleAnchor = (j \ "titleAnchor").asOpt[VegaAnchorPoint],
          titleAlign = (j \ "titleAlign").asOpt[VegaAlign],
          titleAngle = (j \ "titleAngle").asOpt[Double],
          titleBaseline = (j \ "titleBaseline").asOpt[VegaBaseline],
          titleColor = (j \ "titleColor").asOpt[VegaColor],
          titleFont = (j \ "titleFont").asOpt[String],
          titleFontSize = (j \ "titleFontSize").asOpt[Double],
          titleFontStyle = (j \ "titleFontStyle").asOpt[String],
          titleFontWeight = (j \ "titleFontWeight").asOpt[String],
          titleLimit = (j \ "titleLimit").asOpt[Double],
          titleLineHeight = (j \ "titleLineHeight").asOpt[Double],
          titleOpacity = (j \ "titleOpacity").asOpt[Double],
          titlePadding = (j \ "titlePadding").asOpt[Double],
          titleX = (j \ "titleX").asOpt[Double],
          titleY = (j \ "titleY").asOpt[Double],
          translate = (j \ "translate").asOpt[Double],
          values = (j \ "values").asOpt[Seq[Double]],
          zindex = (j \ "zindex").asOpt[Int],
        ))
    },
    new Writes[VegaAxis]{
      def writes(j: VegaAxis) = 
        JsObject(Seq(
          "scale" -> Json.toJson(j.scale),
          "orient" -> Json.toJson(j.orient),
          "bandPosition" -> Json.toJson(j.bandPosition),
          "domain" -> Json.toJson(j.domain),
          "domainCap" -> Json.toJson(j.domainCap),
          "domainColor" -> Json.toJson(j.domainColor),
          "domainDash" -> Json.toJson(j.domainDash),
          "domainDashOffset" -> Json.toJson(j.domainDashOffset),
          "domainOpacity" -> Json.toJson(j.domainOpacity),
          "domainWidth" -> Json.toJson(j.domainWidth),
          "format" -> Json.toJson(j.format),
          "formatType" -> Json.toJson(j.formatType),
          "grid" -> Json.toJson(j.grid),
          "gridCap" -> Json.toJson(j.gridCap),
          "gridColor" -> Json.toJson(j.gridColor),
          "gridDash" -> Json.toJson(j.gridDash),
          "gridDashOffset" -> Json.toJson(j.gridDashOffset),
          "gridScale" -> Json.toJson(j.gridScale),
          "gridWidth" -> Json.toJson(j.gridWidth),
          "labels" -> Json.toJson(j.labels),
          "labelAlign" -> Json.toJson(j.labelAlign),
          "labelAngle" -> Json.toJson(j.labelAngle),
          "labelBaseline" -> Json.toJson(j.labelBaseline),
          "labelBound" -> Json.toJson(j.labelBound),
          "labelColor" -> Json.toJson(j.labelColor),
          "labelFlush" -> Json.toJson(j.labelFlush),
          "labelFlushOffset" -> Json.toJson(j.labelFlushOffset),
          "labelFont" -> Json.toJson(j.labelFont),
          "labelFontSize" -> Json.toJson(j.labelFontSize),
          "labelFontStyle" -> Json.toJson(j.labelFontStyle),
          "labelFontWeight" -> Json.toJson(j.labelFontWeight),
          "labelLimit" -> Json.toJson(j.labelLimit),
          "labelLineHeight" -> Json.toJson(j.labelLineHeight),
          "labelOffset" -> Json.toJson(j.labelOffset),
          "labelOpacity" -> Json.toJson(j.labelOpacity),
          "labelOverlap" -> Json.toJson(j.labelOverlap),
          "labelPadding" -> Json.toJson(j.labelPadding),
          "labelSeparation" -> Json.toJson(j.labelSeparation),
          "minExtent" -> Json.toJson(j.minExtent),
          "maxExtent" -> Json.toJson(j.maxExtent),
          "offset" -> Json.toJson(j.offset),
          "position" -> Json.toJson(j.position),
          "ticks" -> Json.toJson(j.ticks),
          "tickBand" -> Json.toJson(j.tickBand),
          "tickCap" -> Json.toJson(j.tickCap),
          "tickColor" -> Json.toJson(j.tickColor),
          "tickCount" -> Json.toJson(j.tickCount),
          "tickDash" -> Json.toJson(j.tickDash),
          "tickDashOffset" -> Json.toJson(j.tickDashOffset),
          "tickMinStep" -> Json.toJson(j.tickMinStep),
          "tickExtra" -> Json.toJson(j.tickExtra),
          "tickOffset" -> Json.toJson(j.tickOffset),
          "tickOpacity" -> Json.toJson(j.tickOpacity),
          "tickRound" -> Json.toJson(j.tickRound),
          "tickSize" -> Json.toJson(j.tickSize),
          "tickWidth" -> Json.toJson(j.tickWidth),
          "title" -> Json.toJson(j.title),
          "titleAnchor" -> Json.toJson(j.titleAnchor),
          "titleAlign" -> Json.toJson(j.titleAlign),
          "titleAngle" -> Json.toJson(j.titleAngle),
          "titleBaseline" -> Json.toJson(j.titleBaseline),
          "titleColor" -> Json.toJson(j.titleColor),
          "titleFont" -> Json.toJson(j.titleFont),
          "titleFontSize" -> Json.toJson(j.titleFontSize),
          "titleFontStyle" -> Json.toJson(j.titleFontStyle),
          "titleFontWeight" -> Json.toJson(j.titleFontWeight),
          "titleLimit" -> Json.toJson(j.titleLimit),
          "titleLineHeight" -> Json.toJson(j.titleLineHeight),
          "titleOpacity" -> Json.toJson(j.titleOpacity),
          "titlePadding" -> Json.toJson(j.titlePadding),
          "titleX" -> Json.toJson(j.titleX),
          "titleY" -> Json.toJson(j.titleY),
          "translate" -> Json.toJson(j.translate),
          "values" -> Json.toJson(j.values),
          "zindex" -> Json.toJson(j.zindex),
        ).filterNot { _._2 == JsNull }
         .toMap)
    }
  )
}

sealed trait VegaLegendType
object VegaLegendType
{
  case object Symbol extends VegaLegendType
  case object Gradient extends VegaLegendType

  implicit val format = Format[VegaLegendType](
    new Reads[VegaLegendType]{
      def reads(j: JsValue) =
        j.as[String].toLowerCase match {
          case "symbol" => JsSuccess(Symbol)
          case "gradient" => JsSuccess(Gradient)
          case _ => JsError()
        }
    },
    new Writes[VegaLegendType]{
      def writes(j: VegaLegendType) =
        JsString(j match {
          case Symbol => "symbol"
          case Gradient => "gradient"
        })
    }
  )
}

sealed trait VegaDirection
object VegaDirection
{
  case object Vertical extends VegaDirection
  case object Horizontal extends VegaDirection

  implicit val format = Format[VegaDirection](
    new Reads[VegaDirection]{
      def reads(j: JsValue) =
        j.as[String].toLowerCase match {
          case "vertical" => JsSuccess(Vertical)
          case "horizontal" => JsSuccess(Horizontal)
          case _ => JsError()
        }
    },
    new Writes[VegaDirection]{
      def writes(j: VegaDirection) =
        JsString(j match {
          case Vertical => "vertical"
          case Horizontal => "horizontal"
        })
    }
  )
}

sealed trait VegaLegendOrientation
object VegaLegendOrientation
{
  case object Left extends VegaLegendOrientation
  case object Right extends VegaLegendOrientation
  case object Top extends VegaLegendOrientation
  case object Bottom extends VegaLegendOrientation
  case object TopLeft extends VegaLegendOrientation
  case object TopRight extends VegaLegendOrientation
  case object BottomLeft extends VegaLegendOrientation
  case object BottomRight extends VegaLegendOrientation
  case object None extends VegaLegendOrientation

  implicit val format = Format[VegaLegendOrientation](
    new Reads[VegaLegendOrientation]{
      def reads(j: JsValue) =
        j.as[String].toLowerCase match {
          case "left" => JsSuccess(Left)
          case "right" => JsSuccess(Right)
          case "top" => JsSuccess(Top)
          case "bottom" => JsSuccess(Bottom)
          case "top-left" => JsSuccess(TopLeft)
          case "top-right" => JsSuccess(TopRight)
          case "bottom-left" => JsSuccess(BottomLeft)
          case "bottom-right" => JsSuccess(BottomRight)
          case "none" => JsSuccess(None)
          case _ => JsError()
        }
    },
    new Writes[VegaLegendOrientation]{
      def writes(j: VegaLegendOrientation) =
        JsString(j match {
          case Left => "left"
          case Right => "right"
          case Top => "top"
          case Bottom => "bottom"
          case TopLeft => "top-left"
          case TopRight => "top-right"
          case BottomLeft => "bottom-left"
          case BottomRight => "bottom-right"
          case None => "none"
        })
    }
  )
}

sealed trait VegaGridAlign
object VegaGridAlign
{
  case object All extends VegaGridAlign
  case object Each extends VegaGridAlign
  case object None extends VegaGridAlign

  implicit val format = Format[VegaGridAlign](
    new Reads[VegaGridAlign]{
      def reads(j: JsValue) =
        j.as[String].toLowerCase match {
          case "all" => JsSuccess(All)
          case "each" => JsSuccess(Each)
          case "none" => JsSuccess(None)
          case _ => JsError()
        }
    },
    new Writes[VegaGridAlign]{
      def writes(j: VegaGridAlign) =
        JsString(j match {
          case All => "all"
          case Each => "each"
          case None => "none"
        })
    }
  )
}

sealed trait VegaShape
object VegaShape
{
  case object Circle extends VegaShape
  case object Square extends VegaShape
  case object Cross extends VegaShape
  case object Diamond extends VegaShape
  case object TriangleUp extends VegaShape
  case object TriangleDown extends VegaShape
  case object TriangleRight extends VegaShape
  case object TriangleLeft extends VegaShape
  case object Stroke extends VegaShape
  case object Arrow extends VegaShape
  case object Wedge extends VegaShape
  case object Triangle extends VegaShape
  case class SvgShape(path: String) extends VegaShape

  implicit val format = Format[VegaShape](
    new Reads[VegaShape]{
      def reads(j: JsValue) =
        j.as[String].toLowerCase match {
          case "circle" => JsSuccess(Circle)
          case "square" => JsSuccess(Square)
          case "cross" => JsSuccess(Cross)
          case "diamond" => JsSuccess(Diamond)
          case "triangle-up" => JsSuccess(TriangleUp)
          case "triangle-down" => JsSuccess(TriangleDown)
          case "triangle-right" => JsSuccess(TriangleRight)
          case "triangle-left" => JsSuccess(TriangleLeft)
          case "stroke" => JsSuccess(Stroke)
          case "arrow" => JsSuccess(Arrow)
          case "wedge" => JsSuccess(Wedge)
          case "triangle" => JsSuccess(Triangle)
          case path => JsSuccess(SvgShape(path))
        }
    },
    new Writes[VegaShape]{
      def writes(j: VegaShape) =
        JsString(j match {
          case Circle => "circle"
          case Square => "square"
          case Cross => "cross"
          case Diamond => "diamond"
          case TriangleUp => "triangle-up"
          case TriangleDown => "triangle-down"
          case TriangleRight => "triangle-right"
          case TriangleLeft => "triangle-left"
          case Stroke => "stroke"
          case Arrow => "arrow"
          case Wedge => "wedge"
          case Triangle => "triangle"
          case SvgShape(path) => path
        })
    }
  )
}


/**
 * A visualization of scale mappings to visual values (color, shape, size)
 * 
 * See: https://vega.github.io/vega/docs/legends/
 * 
 * The following fields have not been implemented fully (TODO):
 * * encode
 * * format
 * * offset, padding
 */
case class VegaLegend(
  `type`: VegaLegendType,
  direction: Option[VegaDirection] = None,
  orient: Option[VegaLegendOrientation] = None,
  fill: Option[String] = None,
  opacity: Option[String] = None,
  shape: Option[String] = None,
  size: Option[String] = None,
  stroke: Option[String] = None,
  strokeDash: Option[String] = None,
  strokeWidth: Option[String] = None,
  encode: Option[JsValue] = None,
  format: Option[String] = None,
  formatType: Option[VegaAxisFormat] = None,
  gridAlign: Option[VegaGridAlign] = None,
  clipHeight: Option[Double] = None,
  columns: Option[Int] = None,
  columnPadding: Option[Double] = None,
  rowPadding: Option[Double] = None,
  cornerRadius: Option[Double] = None,
  fillColor: Option[VegaColor] = None,
  offset: Option[Double] = None,
  padding: Option[Double] = None,
  strokeColor: Option[VegaColor] = None,
  gradientLength: Option[Double] = None,
  gradientOpacity: Option[Double] = None,
  gradientThickness: Option[Double] = None,
  gradientStrokeColor: Option[VegaColor] = None,
  gradientStrokeWidth: Option[Double] = None,
  labelAlign: Option[VegaAlign] = None,
  labelBaseline: Option[VegaBaseline] = None,
  labelColor: Option[VegaColor] = None,
  labelFont: Option[String] = None,
  labelFontSize: Option[Double] = None,
  labelFontStyle: Option[String] = None,
  labelFontWeight: Option[String] = None,
  labelLimit: Option[Double] = None,
  labelOffset: Option[Double] = None,
  labelOpacity: Option[Double] = None,
  labelOverlap: Option[Boolean] = None,
  labelSeparation: Option[Double] = None,
  legendX: Option[Double] = None,
  legendY: Option[Double] = None,
  symbolDash: Option[Seq[Double]] = None,
  symbolDashOffset: Option[Double] = None,
  symbolFillColor: Option[VegaColor] = None,
  symbolLimit: Option[Double] = None,
  symbolOffset: Option[Double] = None,
  symbolOpacity: Option[Double] = None,
  symbolSize: Option[Double] = None,
  symbolStrokeColor: Option[VegaColor] = None,
  symbolStrokeWidth: Option[Double] = None,
  symbolType: Option[VegaShape] = None,
  tickCount: Option[Double] = None,
  tickMinStep: Option[Double] = None,
  title: Option[String] = None,
  titleAnchor: Option[VegaAnchorPoint] = None,
  titleAlign: Option[VegaAlign] = None,
  titleBaseline: Option[VegaBaseline] = None,
  titleColor: Option[VegaColor] = None,
  titleFont: Option[String] = None,
  titleFontSize: Option[Double] = None,
  titleFontStyle: Option[String] = None,
  titleFontWeight: Option[String] = None,
  titleLimit: Option[Double] = None,
  titleLineHeight: Option[Double] = None,
  titleOpacity: Option[Double] = None,
  titleOrient: Option[VegaOrientation] = None,
  titlePadding: Option[Double] = None,
  values: Option[Seq[JsValue]] = None,
  zindex: Option[Double] = None,
)

object VegaLegend
{
  implicit val format: Format[VegaLegend] = Format[VegaLegend](
    new Reads[VegaLegend]{
      def reads(j: JsValue) = 
        JsSuccess(VegaLegend(
          `type` = (j \ "type").as[VegaLegendType],
          direction = (j \ "direction").asOpt[VegaDirection],
          orient = (j \ "orient").asOpt[VegaLegendOrientation],
          fill = (j \ "fill").asOpt[String],
          opacity = (j \ "opacity").asOpt[String],
          shape = (j \ "shape").asOpt[String],
          size = (j \ "size").asOpt[String],
          stroke = (j \ "stroke").asOpt[String],
          strokeDash = (j \ "strokeDash").asOpt[String],
          strokeWidth = (j \ "strokeWidth").asOpt[String],
          encode = (j \ "encode").asOpt[JsValue],
          format = (j \ "format").asOpt[String],
          formatType = (j \ "formatType").asOpt[VegaAxisFormat],
          gridAlign = (j \ "gridAlign").asOpt[VegaGridAlign],
          clipHeight = (j \ "clipHeight").asOpt[Double],
          columns = (j \ "columns").asOpt[Int],
          columnPadding = (j \ "columnPadding").asOpt[Double],
          rowPadding = (j \ "rowPadding").asOpt[Double],
          cornerRadius = (j \ "cornerRadius").asOpt[Double],
          fillColor = (j \ "fillColor").asOpt[VegaColor],
          offset = (j \ "offset").asOpt[Double],
          padding = (j \ "padding").asOpt[Double],
          strokeColor = (j \ "strokeColor").asOpt[VegaColor],
          gradientLength = (j \ "gradientLength").asOpt[Double],
          gradientOpacity = (j \ "gradientOpacity").asOpt[Double],
          gradientThickness = (j \ "gradientThickness").asOpt[Double],
          gradientStrokeColor = (j \ "gradientStrokeColor").asOpt[VegaColor],
          gradientStrokeWidth = (j \ "gradientStrokeWidth").asOpt[Double],
          labelAlign = (j \ "labelAlign").asOpt[VegaAlign],
          labelBaseline = (j \ "labelBaseline").asOpt[VegaBaseline],
          labelColor = (j \ "labelColor").asOpt[VegaColor],
          labelFont = (j \ "labelFont").asOpt[String],
          labelFontSize = (j \ "labelFontSize").asOpt[Double],
          labelFontStyle = (j \ "labelFontStyle").asOpt[String],
          labelFontWeight = (j \ "labelFontWeight").asOpt[String],
          labelLimit = (j \ "labelLimit").asOpt[Double],
          labelOffset = (j \ "labelOffset").asOpt[Double],
          labelOpacity = (j \ "labelOpacity").asOpt[Double],
          labelOverlap = (j \ "labelOverlap").asOpt[Boolean],
          labelSeparation = (j \ "labelSeparation").asOpt[Double],
          legendX = (j \ "legendX").asOpt[Double],
          legendY = (j \ "legendY").asOpt[Double],
          symbolDash = (j \ "symbolDash").asOpt[Seq[Double]],
          symbolDashOffset = (j \ "symbolDashOffset").asOpt[Double],
          symbolFillColor = (j \ "symbolFillColor").asOpt[VegaColor],
          symbolLimit = (j \ "symbolLimit").asOpt[Double],
          symbolOffset = (j \ "symbolOffset").asOpt[Double],
          symbolOpacity = (j \ "symbolOpacity").asOpt[Double],
          symbolSize = (j \ "symbolSize").asOpt[Double],
          symbolStrokeColor = (j \ "symbolStrokeColor").asOpt[VegaColor],
          symbolStrokeWidth = (j \ "symbolStrokeWidth").asOpt[Double],
          symbolType = (j \ "symbolType").asOpt[VegaShape],
          tickCount = (j \ "tickCount").asOpt[Double],
          tickMinStep = (j \ "tickMinStep").asOpt[Double],
          title = (j \ "title").asOpt[String],
          titleAnchor = (j \ "titleAnchor").asOpt[VegaAnchorPoint],
          titleAlign = (j \ "titleAlign").asOpt[VegaAlign],
          titleBaseline = (j \ "titleBaseline").asOpt[VegaBaseline],
          titleColor = (j \ "titleColor").asOpt[VegaColor],
          titleFont = (j \ "titleFont").asOpt[String],
          titleFontSize = (j \ "titleFontSize").asOpt[Double],
          titleFontStyle = (j \ "titleFontStyle").asOpt[String],
          titleFontWeight = (j \ "titleFontWeight").asOpt[String],
          titleLimit = (j \ "titleLimit").asOpt[Double],
          titleLineHeight = (j \ "titleLineHeight").asOpt[Double],
          titleOpacity = (j \ "titleOpacity").asOpt[Double],
          titleOrient = (j \ "titleOrient").asOpt[VegaOrientation],
          titlePadding = (j \ "titlePadding").asOpt[Double],
          values = (j \ "values").asOpt[Seq[JsValue]],
          zindex = (j \ "zindex").asOpt[Double],
        ))
    },
    new Writes[VegaLegend]{
      def writes(j: VegaLegend) =
        JsObject(Seq(
          "type" -> Json.toJson(j.`type`),
          "direction" -> Json.toJson(j.direction),
          "orient" -> Json.toJson(j.orient),
          "fill" -> Json.toJson(j.fill),
          "opacity" -> Json.toJson(j.opacity),
          "shape" -> Json.toJson(j.shape),
          "size" -> Json.toJson(j.size),
          "stroke" -> Json.toJson(j.stroke),
          "strokeDash" -> Json.toJson(j.strokeDash),
          "strokeWidth" -> Json.toJson(j.strokeWidth),
          "encode" -> Json.toJson(j.encode),
          "format" -> Json.toJson(j.format),
          "formatType" -> Json.toJson(j.formatType),
          "gridAlign" -> Json.toJson(j.gridAlign),
          "clipHeight" -> Json.toJson(j.clipHeight),
          "columns" -> Json.toJson(j.columns),
          "columnPadding" -> Json.toJson(j.columnPadding),
          "rowPadding" -> Json.toJson(j.rowPadding),
          "cornerRadius" -> Json.toJson(j.cornerRadius),
          "fillColor" -> Json.toJson(j.fillColor),
          "offset" -> Json.toJson(j.offset),
          "padding" -> Json.toJson(j.padding),
          "strokeColor" -> Json.toJson(j.strokeColor),
          "gradientLength" -> Json.toJson(j.gradientLength),
          "gradientOpacity" -> Json.toJson(j.gradientOpacity),
          "gradientThickness" -> Json.toJson(j.gradientThickness),
          "gradientStrokeColor" -> Json.toJson(j.gradientStrokeColor),
          "gradientStrokeWidth" -> Json.toJson(j.gradientStrokeWidth),
          "labelAlign" -> Json.toJson(j.labelAlign),
          "labelBaseline" -> Json.toJson(j.labelBaseline),
          "labelColor" -> Json.toJson(j.labelColor),
          "labelFont" -> Json.toJson(j.labelFont),
          "labelFontSize" -> Json.toJson(j.labelFontSize),
          "labelFontStyle" -> Json.toJson(j.labelFontStyle),
          "labelFontWeight" -> Json.toJson(j.labelFontWeight),
          "labelLimit" -> Json.toJson(j.labelLimit),
          "labelOffset" -> Json.toJson(j.labelOffset),
          "labelOpacity" -> Json.toJson(j.labelOpacity),
          "labelOverlap" -> Json.toJson(j.labelOverlap),
          "labelSeparation" -> Json.toJson(j.labelSeparation),
          "legendX" -> Json.toJson(j.legendX),
          "legendY" -> Json.toJson(j.legendY),
          "symbolDash" -> Json.toJson(j.symbolDash),
          "symbolDashOffset" -> Json.toJson(j.symbolDashOffset),
          "symbolFillColor" -> Json.toJson(j.symbolFillColor),
          "symbolLimit" -> Json.toJson(j.symbolLimit),
          "symbolOffset" -> Json.toJson(j.symbolOffset),
          "symbolOpacity" -> Json.toJson(j.symbolOpacity),
          "symbolSize" -> Json.toJson(j.symbolSize),
          "symbolStrokeColor" -> Json.toJson(j.symbolStrokeColor),
          "symbolStrokeWidth" -> Json.toJson(j.symbolStrokeWidth),
          "symbolType" -> Json.toJson(j.symbolType),
          "tickCount" -> Json.toJson(j.tickCount),
          "tickMinStep" -> Json.toJson(j.tickMinStep),
          "title" -> Json.toJson(j.title),
          "titleAnchor" -> Json.toJson(j.titleAnchor),
          "titleAlign" -> Json.toJson(j.titleAlign),
          "titleBaseline" -> Json.toJson(j.titleBaseline),
          "titleColor" -> Json.toJson(j.titleColor),
          "titleFont" -> Json.toJson(j.titleFont),
          "titleFontSize" -> Json.toJson(j.titleFontSize),
          "titleFontStyle" -> Json.toJson(j.titleFontStyle),
          "titleFontWeight" -> Json.toJson(j.titleFontWeight),
          "titleLimit" -> Json.toJson(j.titleLimit),
          "titleLineHeight" -> Json.toJson(j.titleLineHeight),
          "titleOpacity" -> Json.toJson(j.titleOpacity),
          "titleOrient" -> Json.toJson(j.titleOrient),
          "titlePadding" -> Json.toJson(j.titlePadding),
          "values" -> Json.toJson(j.values),
          "zindex" -> Json.toJson(j.zindex),
        ).filterNot { _._2 == JsNull }
         .toMap)
    }
  )
}

/**
 * Descriptive text for a chart
 * 
 * See: https://vega.github.io/vega/docs/title/
 */
case class VegaTitle(
  text: String,
  orient: Option[VegaOrientation] = None,
  align: Option[String] = None,
  anchor: Option[VegaAlign] = None,
  angle: Option[Double] = None,
  baseline: Option[VegaBaseline] = None,
  color: Option[VegaColor] = None,
  dx: Option[Double] = None,
  dy: Option[Double] = None,
  encode: Option[JsObject] = None,
  font: Option[String] = None,
  fontSize: Option[Double] = None,
  fontStyle: Option[String] = None,
  fontWeight: Option[String] = None,
  frame: Option[String] = None,
  limit: Option[Double] = None,
  lineHeight: Option[Double] = None,
  offset: Option[Double] = None,
  subtitleColor: Option[VegaColor] = None,
  subtitleFont: Option[String] = None,
  subtitleFontSize: Option[Double] = None,
  subtitleFontStyle: Option[String] = None,
  subtitleFontWeight: Option[String] = None,
  subtitleLineHeight: Option[Double] = None,
  subtitlePadding: Option[Double] = None,
  zindex: Option[Int] = None,
)
object VegaTitle
{
  implicit val format = Format[VegaTitle](
    new Reads[VegaTitle]{
      def reads(j: JsValue) =
        JsSuccess(VegaTitle(
          text = (j \ "text").as[String],
          orient = (j \ "orient").asOpt[VegaOrientation],
          align = (j \ "align").asOpt[String],
          anchor = (j \ "anchor").asOpt[VegaAlign],
          angle = (j \ "angle").asOpt[Double],
          baseline = (j \ "baseline").asOpt[VegaBaseline],
          color = (j \ "color").asOpt[VegaColor],
          dx = (j \ "dx").asOpt[Double],
          dy = (j \ "dy").asOpt[Double],
          encode = (j \ "encode").asOpt[JsObject],
          font = (j \ "font").asOpt[String],
          fontSize = (j \ "fontSize").asOpt[Double],
          fontStyle = (j \ "fontStyle").asOpt[String],
          fontWeight = (j \ "fontWeight").asOpt[String],
          frame = (j \ "frame").asOpt[String],
          limit = (j \ "limit").asOpt[Double],
          lineHeight = (j \ "lineHeight").asOpt[Double],
          offset = (j \ "offset").asOpt[Double],
          subtitleColor = (j \ "subtitleColor").asOpt[VegaColor],
          subtitleFont = (j \ "subtitleFont").asOpt[String],
          subtitleFontSize = (j \ "subtitleFontSize").asOpt[Double],
          subtitleFontStyle = (j \ "subtitleFontStyle").asOpt[String],
          subtitleFontWeight = (j \ "subtitleFontWeight").asOpt[String],
          subtitleLineHeight = (j \ "subtitleLineHeight").asOpt[Double],
          subtitlePadding = (j \ "subtitlePadding").asOpt[Double],
          zindex = (j \ "zindex").asOpt[Int],
        ))
    },
    new Writes[VegaTitle]{
      def writes(j: VegaTitle) =
        Json.obj(
          "text" -> j.text,
          "orient" -> j.orient,
          "align" -> j.align,
          "anchor" -> j.anchor,
          "angle" -> j.angle,
          "baseline" -> j.baseline,
          "color" -> j.color,
          "dx" -> j.dx,
          "dy" -> j.dy,
          "encode" -> j.encode,
          "font" -> j.font,
          "fontSize" -> j.fontSize,
          "fontStyle" -> j.fontStyle,
          "fontWeight" -> j.fontWeight,
          "frame" -> j.frame,
          "limit" -> j.limit,
          "lineHeight" -> j.lineHeight,
          "offset" -> j.offset,
          "subtitleColor" -> j.subtitleColor,
          "subtitleFont" -> j.subtitleFont,
          "subtitleFontSize" -> j.subtitleFontSize,
          "subtitleFontStyle" -> j.subtitleFontStyle,
          "subtitleFontWeight" -> j.subtitleFontWeight,
          "subtitleLineHeight" -> j.subtitleLineHeight,
          "subtitlePadding" -> j.subtitlePadding,
          "zindex" -> j.zindex,
        )
    }
  )
}

sealed trait VegaMarkType
object VegaMarkType
{
  case object Arc extends VegaMarkType
  case object Area extends VegaMarkType
  case object Image extends VegaMarkType
  case object Group extends VegaMarkType
  case object Line extends VegaMarkType
  case object Path extends VegaMarkType
  case object Rect extends VegaMarkType
  case object Bar extends VegaMarkType
  case object Rule extends VegaMarkType
  case object Shape extends VegaMarkType
  case object Symbol extends VegaMarkType
  case object Text extends VegaMarkType
  case object Trail extends VegaMarkType

  implicit val format = Format[VegaMarkType](
    new Reads[VegaMarkType]{
      def reads(j: JsValue) =
        j.as[String].toLowerCase match {
          case "arc" => JsSuccess(Arc)
          case "area" => JsSuccess(Area)
          case "image" => JsSuccess(Image)
          case "group" => JsSuccess(Group)
          case "line" => JsSuccess(Line)
          case "path" => JsSuccess(Path)
          case "rect" => JsSuccess(Rect)
          case "bar" => JsSuccess(Bar)
          case "rule" => JsSuccess(Rule)
          case "shape" => JsSuccess(Shape)
          case "symbol" => JsSuccess(Symbol)
          case "text" => JsSuccess(Text)
          case "trail" => JsSuccess(Trail)
          case _ => JsError()
        }
    },
    new Writes[VegaMarkType]{
      def writes(j: VegaMarkType) =
        JsString(j match {
          case Arc => "arc"
          case Area => "area"
          case Image => "image"
          case Group => "group"
          case Line => "line"
          case Path => "path"
          case Rect => "rect"
          case Bar => "bar"
          case Rule => "rule"
          case Shape => "shape"
          case Symbol => "symbol"
          case Text => "text"
          case Trail => "trail"
        })
    }
  )
}

case class VegaFacet(
  name: String,
  data: String,
  field: Option[String] = None,
  groupby: Option[String] = None,
  aggregate: Option[JsObject] = None
)
object VegaFacet
{
  implicit val format: Format[VegaFacet] = Json.format
}

case class VegaFrom(
  data: String,
  facet: Option[VegaFacet] = None
)
object VegaFrom
{
  implicit val format: Format[VegaFrom] = Json.format
}

/**
 * A Vega Value Reference
 * 
 * See: https://vega.github.io/vega/docs/types/#Value
 */
sealed trait VegaValueReference
{
  def scale(s: String) = 
    VegaValueReference.ScaleTransform(s, this)
}
object VegaValueReference
{
  /**
   * Basic values
   */
  case class Literal(
    value: JsValue,
  ) extends VegaValueReference
  /**
   * References to fields
   */
  case class Field(
    field: String,
  ) extends VegaValueReference
  case class Signal(
    signal: String
  ) extends VegaValueReference
  /**
   * Extend the target value with a scale transformation
   */
  case class ScaleTransform(
    scale: String,
    target: VegaValueReference
  ) extends VegaValueReference

  /**
   * Used for Width of bar chart 
   */
  case class Band(
    band: Int
  ) extends VegaValueReference

  implicit val fieldFormat: Format[Field] = Json.format
  implicit val valueFormat: Format[Literal] = Json.format
  implicit val signalFormat: Format[Signal] = Json.format
  implicit val scaleBandRefFormat: Format[Band] = Json.format
  implicit val scaleFormat: Format[ScaleTransform] = Format[ScaleTransform](
    new Reads[ScaleTransform]{
      def reads(j: JsValue): JsResult[ScaleTransform] =
        j match {
          case JsObject(elems) if elems contains "scale" => 
            JsSuccess(
              ScaleTransform(elems("scale").as[String], 
                format.reads(
                  JsObject(elems - "scale")
                ).get
              )
            )
          case _ => JsError()
        }
    },
    new Writes[ScaleTransform]{
      def writes(j: ScaleTransform): JsValue = 
        Json.toJson(j.target) match {
          case JsObject(elems) => 
            JsObject(elems ++ Map("scale" -> JsString(j.scale)))
          case x => 
            JsObject(Map("scale" -> JsString(j.scale), "value" -> x))
        }
    }
  )
  implicit val format: Format[VegaValueReference] = Format[VegaValueReference](
    new Reads[VegaValueReference]{
      def reads(j: JsValue): JsResult[VegaValueReference] =
        JsSuccess(
          j match {
            case JsObject(elems) =>
              if(elems contains "scale"){
                j.as[ScaleTransform]
              } else if(elems contains "field"){
                j.as[Field]
              } else if(elems contains "band"){
                j.as[Band]
              } else if(elems contains "signal"){
                j.as[Signal]
              } else {
                j.as[Literal]
              }
            case _ => j.as[Literal]
          }
        )
    },
    new Writes[VegaValueReference]{
      def writes(j: VegaValueReference): JsValue =
        j match {
          case j:Field => Json.toJson(j)
          case j:Literal => Json.toJson(j)
          case j:ScaleTransform => Json.toJson(j)
          case j:Signal => Json.toJson(j)
          case j:Band => Json.toJson(j)
        }
    }
  )
}

//Signal Attribute
case class VegaSignalEncoding(
  signal: String
)
object VegaSignalEncoding
{
  implicit val format: Format[VegaSignalEncoding] = Json.format
}

/**
 * A visual encoding of a mark
 * 
 * See: https://vega.github.io/vega/docs/marks/#visual-encoding
 * 
 * 2023-05-06 by OK: Honestly, encodings are something that I don't
 * fully understand at this point.  There seems to be a hideous lack
 * of documentation on what exactly is allowed here.  The below 
 * definition is almost certainly wrong as a result... but it is 
 * sufficient to get a line.  I **think** the encoding is specific
 * to the VegaMarkType used for the mark, but there doesn't seem to
 * be a clear documentation of what's allowed anywhere.
 * 
 * TODO: Track down the full schema and plug it in here.
 */

case class VegaMarkEncoding(
  x: Option[VegaValueReference] = None,
  y: Option[VegaValueReference] = None,
  width: Option[VegaValueReference] = None,
  y2: Option[VegaValueReference] = None,
  stroke: Option[VegaValueReference] = None,
  fill: Option[VegaValueReference] = None,
  tooltip: Option[VegaValueReference] = None,
  opacity: Option[Double] = None
)
object VegaMarkEncoding
{
  implicit val format: Format[VegaMarkEncoding] = Json.format
} 

case class VegaMarkEncodingGroup(
  enter: Option[VegaMarkEncoding] = None,
  update: Option[VegaMarkEncoding] = None,
)
object VegaMarkEncodingGroup
{
  implicit val format: Format[VegaMarkEncodingGroup] = Json.format
}

/**
 * A visual encoding of data using geometric primitives.
 * 
 * See: https://vega.github.io/vega/docs/marks/
 */
case class VegaMark(
  `type`: VegaMarkType,
  clip: Option[Boolean] = None,
  encode: Option[VegaMarkEncodingGroup] = None,
  from: Option[VegaFrom] = None,
  interactive: Option[Boolean] = None,
  key: Option[String] = None,
  name: Option[String] = None,
  on: Option[JsValue] = None,
  sort: Option[JsValue] = None,
  transform: Option[JsValue] = None,
  role: Option[String] = None,
  style: Option[String] = None,
  zindex: Option[Int] = None,
  marks: Option[Seq[VegaMark]] = None,
)
object VegaMark
{
  implicit val format: Format[VegaMark] = Json.format
}

/**
 * The base class for a vega chart.  
 * 
 * See: https://vega.github.io/vega/docs/specification/
 * 
 * Specific features are left as TODOs for later:
 * * config: These options override settings present in the chart.  It
 *           may be useful to have this in the future, but for now,
 *           it's a lot of spec that can be cut.
 * * signals: Signals allow for dynamic properties to be specified.
 *            Things like the width/height/background/etc... can be 
 *            defined in terms of signals.  Making this work without 
 *            resorting to Any types is going to be a bit of a PitA.
 *            If we don't need it yet, then let's hold off on 
 *            implementing it for now.
 * * projections: Projections are used to visualize geospatial data.
 *                This is definitely of interest, but at the moment
 *                projections are a pretty complex structure:
 *                (https://vega.github.io/vega/docs/projections/)
 *                that we don't need yet.  
 */
case class VegaChart(
  description: String,
  width: Int,
  height: Int,
  padding: VegaPadding = new VegaPadding(5),
  autosize: VegaAutosize = VegaAutosize.NoPadding,
  background: Option[VegaColor] = None,
  title: Option[VegaTitle] = None,
  config: Option[JsValue] = None,
  signals: Seq[JsValue] = Seq(),
  data: Seq[VegaData] = Seq(),
  axes: Seq[VegaAxis] = Seq(),
  scales: Seq[VegaScale] = Seq(),
  legends: Seq[VegaLegend] = Seq(),
  projections: Seq[JsValue] = Seq(),
  marks: Seq[VegaMark] = Seq(),
  encode: Option[JsObject] = None,
  usermeta: Option[JsObject] = None,
  `$schema`: String = "https://vega.github.io/schema/vega/v5.json",
)
object VegaChart
{
  implicit val format: Format[VegaChart] = Json.format
}
