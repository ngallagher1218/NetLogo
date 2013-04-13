// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.mirror

import java.awt.image.BufferedImage

import org.nlogo.api
import org.nlogo.api.Action
import org.nlogo.plot.Plot

class ModelRun(
  var name: String,
  val modelString: String,
  val viewArea: java.awt.geom.Area,
  val fixedViewSettings: FixedViewSettings,
  val interfaceImage: BufferedImage,
  val initialPlots: Seq[Plot],
  val initialDrawingImage: BufferedImage,
  private var _generalNotes: String = "",
  private var _indexedNotes: List[IndexedNote] = Nil)
  extends api.ModelRun
  with SavableRun {

  var stillRecording = true
  var dirty: Boolean = false

  def generalNotes = _generalNotes
  def generalNotes_=(text: String) { _generalNotes = text; dirty = true }

  def indexedNotes = _indexedNotes
  def indexedNotes_=(notes: List[IndexedNote]) { _indexedNotes = notes; dirty = true }

  private var _deltas = IndexedSeq[Delta]()
  def deltas = _deltas
  def size = _deltas.length

  var currentFrameIndex: Option[Int] = None
  def currentFrame: Option[Frame] = currentFrameIndex.flatMap(frame)
  def lastFrameIndex = if (size > 0) Some(size - 1) else None
  def lastFrame: Option[Frame] = lastFrameIndex.flatMap(frameCache.get)

  override def toString = (if (dirty) "* " else "") + name

  def load(deltas: Seq[Delta]) {
    deltas.foreach(appendFrame)
    stillRecording = false
  }

  private val frameCache = new FrameCache(deltas _, 10, 20)
  def frame(index: Int) = frameCache.get(index)

  private def appendFrame(delta: Delta) {
    val newFrame = lastFrame
      .getOrElse(Frame(Map(), initialPlots, initialDrawingImage))
      .applyDelta(delta)
    frameCache.add(size, newFrame)
    _deltas :+= delta // added at the end not to mess up lastFrameIndex and size
  }

  def appendData(mirrorables: Iterable[Mirrorable], actions: IndexedSeq[Action]) {
    val oldMirroredState = lastFrame.map(_.mirroredState).getOrElse(Map())
    val (newMirroredState, mirroredUpdate) = Mirroring.diffs(oldMirroredState, mirrorables)
    val delta = Delta(Serializer.toBytes(mirroredUpdate), actions)
    appendFrame(delta)
    dirty = true
  }
}

case class Delta(
  val rawMirroredUpdate: Array[Byte],
  val actions: IndexedSeq[Action]) {
  def mirroredUpdate: Update = Serializer.fromBytes(rawMirroredUpdate)
  def size = rawMirroredUpdate.size + actions.size // used in FrameCache cost calculations
}