package info.vizierdb

object types 
{
  type Identifier = Long

  object ActionType extends Enumeration
  {
    type T = Value

    val CREATE,
        APPEND,
        DELETE,
        INSERT,
        UPDATE = Value
  }

  object ExecutionState extends Enumeration
  {
    type T = Value

    val DONE   = Value(1, "DONE")    /* The referenced execution is correct and up-to-date */
    val ERROR   = Value(2, "ERROR")    /* The cell or a cell preceding it is affected by a notebook 
                                          error */
    val WAITING = Value(3, "WAITING")  /* The referenced execution follows a stale cell and *may* be 
                                          out-of-date, depending on the outcome of the stale cell  */
    val BLOCKED = Value(4, "BLOCKED")  /* The referenced execution is incorrect for this workflow and 
                                          needs to be recomputed, but is blocked on another one */
    val STALE   = Value(5, "STALE")    /* The referenced execution is incorrect for this workflow and 
                                          needs to be recomputed */
  }

  object ArtifactType extends Enumeration
  {
    type T = Value

    val DATASET  = Value(1, "Dataset")
    val FUNCTION = Value(2, "Function")
    val BLOB     = Value(3, "Blob")
  }
}


