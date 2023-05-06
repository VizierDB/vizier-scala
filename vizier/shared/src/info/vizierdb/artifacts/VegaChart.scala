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
  format: Option[VegaFileFormat],
  source: Option[Seq[String]],
  url: Option[String],
  values: Seq[JsObject],
  async: Boolean = false,
  // on: Seq[VegaTrigger]
  // transform: Seq[VegaTransform]
)
object VegaData
{
  implicit val format: Format[VegaData] = Json.format
}

/**
 * A vega scale type
 */
sealed trait VegaScaleType
sealed trait VegaQuantitiativeScale extends VegaScaleType
case object VegaLinearScale extends VegaQuantitiativeScale
case object VegaLogScale extends VegaQuantitiativeScale
case object VegaPowScale extends VegaQuantitiativeScale
case object VegaSqrtScale extends VegaQuantitiativeScale
case object VegaSymlogScale extends VegaQuantitiativeScale
case object VegaTimeScale extends VegaQuantitiativeScale
case object VegaUTCScale extends VegaQuantitiativeScale
case object VegaSequentialScale extends VegaQuantitiativeScale

sealed trait VegaDiscreteScale extends VegaScaleType
case object VegaOrdinalScale extends VegaDiscreteScale
case object VegaBandScale extends VegaDiscreteScale
case object VegaPointScale extends VegaDiscreteScale

sealed trait VegaDiscretizingScale extends VegaScaleType
case object VegaQuantileScale extends VegaDiscretizingScale
case object VegaQuantizeScale extends VegaDiscretizingScale
case object VegaThresholdScale extends VegaDiscretizingScale
case object VegaBinOrdinalScale extends VegaDiscretizingScale

object VegaScaleType
{
  implicit val format = Format[VegaScaleType](
    new Reads[VegaScaleType]{
      def reads(j: JsValue) = 
        j.as[String].toLowerCase match {
          case "linear" => JsSuccess(VegaLinearScale)
          case "log" => JsSuccess(VegaLogScale)
          case "pow" => JsSuccess(VegaPowScale)
          case "sqrt" => JsSuccess(VegaSqrtScale)
          case "symlog" => JsSuccess(VegaSymlogScale)
          case "time" => JsSuccess(VegaTimeScale)
          case "utc" => JsSuccess(VegaUTCScale)
          case "sequential" => JsSuccess(VegaSequentialScale)
          case "ordinal" => JsSuccess(VegaOrdinalScale)
          case "band" => JsSuccess(VegaBandScale)
          case "point" => JsSuccess(VegaPointScale)
          case "quantile" => JsSuccess(VegaQuantileScale)
          case "quantize" => JsSuccess(VegaQuantizeScale)
          case "threshold" => JsSuccess(VegaThresholdScale)
          case "bin-ordinal" => JsSuccess(VegaBinOrdinalScale)
          case _ => JsError()
        }
    },
    new Writes[VegaScaleType]{
      def writes(j: VegaScaleType) =
        JsString(j match {
          case VegaLinearScale => "linear"
          case VegaLogScale => "log"
          case VegaPowScale => "pow"
          case VegaSqrtScale => "sqrt"
          case VegaSymlogScale => "symlog"
          case VegaTimeScale => "time"
          case VegaUTCScale => "utc"
          case VegaSequentialScale => "sequential"
          case VegaOrdinalScale => "ordinal"
          case VegaBandScale => "band"
          case VegaPointScale => "point"
          case VegaQuantileScale => "quantile"
          case VegaQuantizeScale => "quantize"
          case VegaThresholdScale => "threshold"
          case VegaBinOrdinalScale => "bin-ordinal"
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
  range: Seq[JsValue] = Seq(),
  domain: Seq[JsValue] = Seq(),
  interpolate: Option[VegaInterpolationMethod] = None,
  reverse: Option[Boolean] = None,
  round:   Option[Boolean] = None,
  domainRaw: Option[Seq[Double]] = None,
  domainMax: Option[Double] = None,
  domainMin: Option[Double] = None,
  domainMid: Option[Double] = None,
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
        Json.obj(
          "scale" -> j.scale,
          "orient" -> j.orient,
          "bandPosition" -> j.bandPosition,
          "domain" -> j.domain,
          "domainCap" -> j.domainCap,
          "domainColor" -> j.domainColor,
          "domainDash" -> j.domainDash,
          "domainDashOffset" -> j.domainDashOffset,
          "domainOpacity" -> j.domainOpacity,
          "domainWidth" -> j.domainWidth,
          "format" -> j.format,
          "formatType" -> j.formatType,
          "grid" -> j.grid,
          "gridCap" -> j.gridCap,
          "gridColor" -> j.gridColor,
          "gridDash" -> j.gridDash,
          "gridDashOffset" -> j.gridDashOffset,
          "gridScale" -> j.gridScale,
          "gridWidth" -> j.gridWidth,
          "labels" -> j.labels,
          "labelAlign" -> j.labelAlign,
          "labelAngle" -> j.labelAngle,
          "labelBaseline" -> j.labelBaseline,
          "labelBound" -> j.labelBound,
          "labelColor" -> j.labelColor,
          "labelFlush" -> j.labelFlush,
          "labelFlushOffset" -> j.labelFlushOffset,
          "labelFont" -> j.labelFont,
          "labelFontSize" -> j.labelFontSize,
          "labelFontStyle" -> j.labelFontStyle,
          "labelFontWeight" -> j.labelFontWeight,
          "labelLimit" -> j.labelLimit,
          "labelLineHeight" -> j.labelLineHeight,
          "labelOffset" -> j.labelOffset,
          "labelOpacity" -> j.labelOpacity,
          "labelOverlap" -> j.labelOverlap,
          "labelPadding" -> j.labelPadding,
          "labelSeparation" -> j.labelSeparation,
          "minExtent" -> j.minExtent,
          "maxExtent" -> j.maxExtent,
          "offset" -> j.offset,
          "position" -> j.position,
          "ticks" -> j.ticks,
          "tickBand" -> j.tickBand,
          "tickCap" -> j.tickCap,
          "tickColor" -> j.tickColor,
          "tickCount" -> j.tickCount,
          "tickDash" -> j.tickDash,
          "tickDashOffset" -> j.tickDashOffset,
          "tickMinStep" -> j.tickMinStep,
          "tickExtra" -> j.tickExtra,
          "tickOffset" -> j.tickOffset,
          "tickOpacity" -> j.tickOpacity,
          "tickRound" -> j.tickRound,
          "tickSize" -> j.tickSize,
          "tickWidth" -> j.tickWidth,
          "title" -> j.title,
          "titleAnchor" -> j.titleAnchor,
          "titleAlign" -> j.titleAlign,
          "titleAngle" -> j.titleAngle,
          "titleBaseline" -> j.titleBaseline,
          "titleColor" -> j.titleColor,
          "titleFont" -> j.titleFont,
          "titleFontSize" -> j.titleFontSize,
          "titleFontStyle" -> j.titleFontStyle,
          "titleFontWeight" -> j.titleFontWeight,
          "titleLimit" -> j.titleLimit,
          "titleLineHeight" -> j.titleLineHeight,
          "titleOpacity" -> j.titleOpacity,
          "titlePadding" -> j.titlePadding,
          "titleX" -> j.titleX,
          "titleY" -> j.titleY,
          "translate" -> j.translate,
          "values" -> j.values,
          "zindex" -> j.zindex,
        )
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
        Json.obj(
          "type" -> j.`type`,
          "direction" -> j.direction,
          "orient" -> j.orient,
          "fill" -> j.fill,
          "opacity" -> j.opacity,
          "shape" -> j.shape,
          "size" -> j.size,
          "stroke" -> j.stroke,
          "strokeDash" -> j.strokeDash,
          "strokeWidth" -> j.strokeWidth,
          "encode" -> j.encode,
          "format" -> j.format,
          "formatType" -> j.formatType,
          "gridAlign" -> j.gridAlign,
          "clipHeight" -> j.clipHeight,
          "columns" -> j.columns,
          "columnPadding" -> j.columnPadding,
          "rowPadding" -> j.rowPadding,
          "cornerRadius" -> j.cornerRadius,
          "fillColor" -> j.fillColor,
          "offset" -> j.offset,
          "padding" -> j.padding,
          "strokeColor" -> j.strokeColor,
          "gradientLength" -> j.gradientLength,
          "gradientOpacity" -> j.gradientOpacity,
          "gradientThickness" -> j.gradientThickness,
          "gradientStrokeColor" -> j.gradientStrokeColor,
          "gradientStrokeWidth" -> j.gradientStrokeWidth,
          "labelAlign" -> j.labelAlign,
          "labelBaseline" -> j.labelBaseline,
          "labelColor" -> j.labelColor,
          "labelFont" -> j.labelFont,
          "labelFontSize" -> j.labelFontSize,
          "labelFontStyle" -> j.labelFontStyle,
          "labelFontWeight" -> j.labelFontWeight,
          "labelLimit" -> j.labelLimit,
          "labelOffset" -> j.labelOffset,
          "labelOpacity" -> j.labelOpacity,
          "labelOverlap" -> j.labelOverlap,
          "labelSeparation" -> j.labelSeparation,
          "legendX" -> j.legendX,
          "legendY" -> j.legendY,
          "symbolDash" -> j.symbolDash,
          "symbolDashOffset" -> j.symbolDashOffset,
          "symbolFillColor" -> j.symbolFillColor,
          "symbolLimit" -> j.symbolLimit,
          "symbolOffset" -> j.symbolOffset,
          "symbolOpacity" -> j.symbolOpacity,
          "symbolSize" -> j.symbolSize,
          "symbolStrokeColor" -> j.symbolStrokeColor,
          "symbolStrokeWidth" -> j.symbolStrokeWidth,
          "symbolType" -> j.symbolType,
          "tickCount" -> j.tickCount,
          "tickMinStep" -> j.tickMinStep,
          "title" -> j.title,
          "titleAnchor" -> j.titleAnchor,
          "titleAlign" -> j.titleAlign,
          "titleBaseline" -> j.titleBaseline,
          "titleColor" -> j.titleColor,
          "titleFont" -> j.titleFont,
          "titleFontSize" -> j.titleFontSize,
          "titleFontStyle" -> j.titleFontStyle,
          "titleFontWeight" -> j.titleFontWeight,
          "titleLimit" -> j.titleLimit,
          "titleLineHeight" -> j.titleLineHeight,
          "titleOpacity" -> j.titleOpacity,
          "titleOrient" -> j.titleOrient,
          "titlePadding" -> j.titlePadding,
          "values" -> j.values,
          "zindex" -> j.zindex,
        )
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
  facet: Option[VegaFacet]
)
object VegaFrom
{
  implicit val format: Format[VegaFrom] = Json.format
}

/**
 * A visual encoding of data using geometric primitives.
 * 
 * See: https://vega.github.io/vega/docs/marks/
 */
case class VegaMark(
  `type`: VegaMarkType,
  clip: Option[Boolean],
  encode: Option[JsObject],
  from: Option[VegaFrom],
  interactive: Option[Boolean],
  key: Option[String],
  name: Option[String],
  on: Option[JsValue],
  sort: Option[JsValue],
  transform: Option[JsValue],
  role: Option[String],
  style: Option[String],
  zindex: Option[Int],
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
  background: VegaColor,
  width: Int,
  height: Int,
  padding: VegaPadding,
  autosize: VegaAutosize,
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
