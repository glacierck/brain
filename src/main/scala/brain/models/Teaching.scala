/**
 * Copyright 2013 Israel Freitas (israel.araujo.freitas@gmail.com)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brain.models

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import com.ansvia.graph.BlueprintsWrapper._
import com.ansvia.graph.annotation.Persistent
import net.liftweb.json._
import net.liftweb.common.Box
import net.liftweb.util.Helpers
import com.tinkerpop.blueprints.Graph
import com.tinkerpop.blueprints.Vertex
import brain.db.GraphDb
import com.tinkerpop.blueprints.TransactionalGraph
import aimltoxml.aiml.TemplateElement
import aimltoxml.aiml.Text
import aimltoxml.aiml.Category
import aimltoxml.aiml.Srai
import aimltoxml.aiml.Random
import aimltoxml.aiml.Get
import aimltoxml.aiml.AimlSet
import aimltoxml.aiml.AimlSet
import scala.collection.mutable.ListBuffer
import aimltoxml.aiml.Get
import aimltoxml.aiml.Think
import aimltoxml.aiml.RandomElement
import aimltoxml.aiml.Star
import aimltoxml.aiml.Get
import scala.util.matching.Regex.MatchIterator

case class Teaching(whenTheUserSays:String, say:String) extends DbObject{
    require(!whenTheUserSays.isEmpty, "Field 'when the user says', can not be empty.")
    require(!say.isEmpty, "Field 'say', can not be empty.")
    
    var id:Option[String] = None
	var topicId:Option[String] = None
	
	@Persistent var respondingTo:String = null
	@Persistent var memorize:String = null
    
    def toAiml:Set[Category] = new TeachingToCategoryAdapter(this).toCategory
    
    def save()(implicit db:TransactionalGraph):Vertex = transact{
        if(this.respondingTo == null || this.respondingTo.trim().equals("")) this.respondingTo = null
        if(this.memorize == null || this.memorize.trim().equals("")) this.memorize = null
        
        validateMemorize
        
        if(this.id.isDefined){
            this.update
        }
        else{
        	val that = super.save()
			db.getVertex(topicId.get) --> "include" --> that
			that
        }
        
    }
    
    private def validateMemorize() = {
        if(this.memorize != null){
            this.memorize.split("\n").map(_.trim).filter(!_.isEmpty).toList.foreach({ keyValue =>
                try {
                    MemorizeUtil.validate(keyValue)
                }
                catch {
                    case e: Throwable => throw new Exception(s"Invalid syntax in 'Memorize' field. ${e.getMessage}")
                }
            })
        }
    }
    
    def update()(implicit db:TransactionalGraph) = transact{
        validateMemorize
        
        val vertex = db.getVertex(this.id.get)
		vertex.setProperty("whenTheUserSays", this.whenTheUserSays)
		if(this.respondingTo!=null) vertex.setProperty("respondingTo", this.respondingTo)
		if(this.memorize!=null)     vertex.setProperty("memorize",     this.memorize)
		vertex.setProperty("say", this.say)
		vertex
    }
    
    def destroy() (implicit db:TransactionalGraph) = transact { 
        db removeVertex getVertex 
    }

}

object Teaching extends PersistentName {
    private implicit val formats = net.liftweb.json.DefaultFormats

    implicit def toJson(teaching: Teaching): JValue = JObject(
        JField("id", JString(teaching.id.get.replace("#", ""))) ::
        JField("whenTheUserSays", JString(teaching.whenTheUserSays)) 		::
        JField("respondingTo", JString(teaching.respondingTo)) 			::
        JField("memorize", JString(teaching.memorize)) 				::
        JField("say", JString(teaching.say)) 					::
        JField("topicId", JString(teaching.topicId.get.replace("#", ""))) :: 
        Nil
    )
    
    implicit def topicSetToJValue(topics: Set[Teaching]): JValue = JArray(topics.map(toJson).toList)
    
    def findAll()(implicit db:Graph):Set[Knowledge] = query().vertices().toSet[Vertex].map(v=>Knowledge(v))
    
    def findById(id:String)(implicit db:Graph):Teaching = Teaching(db.getVertex(id))
    
    def findByTopic(topic:Topic)(implicit db:Graph):Set[Teaching] = topic.getVertex.pipe.out("include").iterator.toSet[Vertex].map(v=>Teaching(v))
    
    def apply(in: JValue):Box[Teaching] = Helpers.tryo{
        try {
	        val id = (in \ "id") match {
        	    case id: JString => Some(id.values)
        	    case _ =>  None
        	}
	        val topicId = (in \ "topicId") match {
        	    case topicId: JString => Some(topicId.values)
        	    case _ => None
        	}
	        val whenTheUserSays = (in \ "whenTheUserSays") match {
		        case whenTheUserSays: JString => whenTheUserSays.values
		        case _ =>  ""
	        }
	        val respondingTo = (in \ "respondingTo") match {
		        case respondingTo: JString => Some(respondingTo.values)
		        case _ =>  None
	        }
	        val memorize = (in \ "memorize") match {
		        case memorize: JString => Some(memorize.values)
		        case _ =>  None
	        }
	        val say = (in \ "say") match {
		        case say: JString => say.values
		        case _ =>  ""
	        }
	        
	        val teaching = Teaching(whenTheUserSays, say)
	        teaching.id = id
	        teaching.topicId = topicId
	        respondingTo map { teaching.respondingTo = _}
	        memorize map { teaching.memorize = _}
	        teaching
		}
        catch{
            case t:Throwable => t.printStackTrace(); throw t
        }
    }
    def unapply(in:JValue):Option[Teaching] = apply(in)
    
    def unapply(in:Any):Option[(Option[String], Option[String], Option[String], Option[String], String, String)] = {
        in match {
            case teaching : Teaching => {
               val respondingTo = if(teaching.respondingTo == null) None else Some(teaching.respondingTo)
               val memorize = if(teaching.memorize == null) None else Some(teaching.memorize)
               Some((teaching.id, teaching.topicId, respondingTo, memorize, teaching.whenTheUserSays, teaching.say))
            }
            case id : String => {
            	implicit val db = GraphDb.get
				try{
					val teaching = Teaching.findById(id) 
	        		val respondingTo = if(teaching.respondingTo == null) None else Some(teaching.respondingTo)
	        		val memorize = if(teaching.memorize == null) None else Some(teaching.memorize)
	        		Some((teaching.id, teaching.topicId, respondingTo, memorize, teaching.whenTheUserSays, teaching.say))
				}
	        	catch{
	        	    case t: Throwable => None
	        	}
	        	finally{
	        		db.shutdown()
	        	}
            }
            case _ => None
        }
    }
    
    def apply(vertex:Vertex)(implicit db:Graph):Teaching = {
        val teaching = vertex.toCC[Teaching].get
        teaching.id = Some(vertex.getId.toString)
        teaching.topicId = Some(vertex.pipe.in("include").iterator.next().getId().toString())
        teaching
    }
}

class TeachingToCategoryAdapter(teaching: Teaching) {
    require(teaching != null)
    
    val respondingTo  : String 	     = if(teaching.respondingTo != null && !teaching.respondingTo.trim().isEmpty()) teaching.respondingTo else "*"
    val whatWasSaid   : Set[String]  = StringUtil.linesToSet(teaching.whenTheUserSays)
    val whatToMemorize: List[String] = StringUtil.linesToList(teaching.memorize)
    val whatToSay     : Set[String]  = StringUtil.linesToSet(teaching.say)
    
    def toCategory: Set[Category] = {
        val defaultPattern = selectDefaultPattern(whatWasSaid)
        whatWasSaid.map(createCategory(_, defaultPattern, respondingTo, whatToMemorize, whatToSay))
    }
    
   	def createCategory(whatWasSaid: String, defaultPattern: String, respondingTo: String, whatToMemorize: List[String], whatToSay: Set[String]):Category = {
        if (whatWasSaid == defaultPattern)	Category(whatWasSaid, createTemplateElements(whatToMemorize, whatToSay), respondingTo)
        else 								Category(whatWasSaid, Set[TemplateElement](Srai(defaultPattern)), respondingTo)
    }
   	
    def createTemplateElements(memorize: List[String], say: Set[String]):Set[TemplateElement] = Set(parseMemorize(memorize), parseSay(say.toList))

    def parseMemorize(listOfThingsToMemorize: List[String]): Think = {
        Think(listOfThingsToMemorize.map { keyValue =>
            KeyValueValidator.validateKeyValue(keyValue)
            val key   = KeyValueUtil.findKey(keyValue).get
            val value = KeyValueUtil.findValue(keyValue).get
            AimlSet(key, parseText(value))
        })
    }
    def parseSay(whatToSay: List[String]): Random = new Random(whatToSay.map { parseText(_) }.toSet.asInstanceOf[Set[List[RandomElement]]])
    
    /**
     * 
     * - "test"          => Text('test')
     * - "${test}"       => Get("test")
     * - "hello ${name}" => Text("hello "), Get("name")
     */
    def parseText(valueString:String):List[TemplateElement] = {
		val iteratorOfGet = GetUtil.findAllAndParse(valueString)
        val splitedValue  = valueString.split(GetUtil.GetSyntaxRegex.toString)
        val result: ListBuffer[TemplateElement] = new ListBuffer
        
        if ( splitedValue.isEmpty ) while(iteratorOfGet.hasNext)result.add(iteratorOfGet.next)
        else splitedValue.foreach{txt=> result.add(Text(txt)); if(iteratorOfGet.hasNext) result.add(iteratorOfGet.next)}
    	result.toList
    }
    
    def selectDefaultPattern(setOfWhatWasSaid: Set[String]) = {
        var defaultPattern         = ""
        var lowerPatternComplexity = 100.0
        var patternComplexity      = 100.0

        setOfWhatWasSaid.foreach { whatWasSaid =>
            patternComplexity = calculateThePatternComplexity(whatWasSaid)
            if (patternComplexity < lowerPatternComplexity) {
                lowerPatternComplexity = patternComplexity
                defaultPattern = whatWasSaid
            }
        }
        defaultPattern
    }

    // it should be a selectDefaultPattern's local function, but is not for tests purposes.
    def calculateThePatternComplexity(pattern: String): Double = {
        def countStarsIn(p: String) = countSpecialChar("*", p)
        def countUnderscoreIn(p: String) = countSpecialChar("_", p)

        val amountOfChar = pattern.length
        val amountOfStar = countStarsIn(pattern)
        val amountOfUnderscore = countUnderscoreIn(pattern)

        amountOfChar * 0.001 + amountOfStar * 1 + amountOfUnderscore * 1
    }

    // it should be a calculateThePatternComplexity's local function, but is not for tests purposes.
    def countSpecialChar(c: String, p: String) = { p.split("\\" + c + "+", -1).size - 1 }

}

object StringUtil {
    def linesToSet(aText:String):Set[String]   = if(aText == null)Set.empty[String]  else aText.split("\n").map(_.trim).filter(!_.isEmpty).toSet
	def linesToList(aText:String):List[String] = if(aText == null)List.empty[String] else aText.split("\n").map(_.trim).filter(!_.isEmpty).toList
}

object MemorizeUtil {
    def validate(memorizeFieldContent:String):Unit = validate(StringUtil.linesToList(memorizeFieldContent))
    def validate(listOfKeyValue:List[String]):Unit = listOfKeyValue.foreach(KeyValueValidator.validateKeyValue(_))
}


object GetUtil {
	final val GetSyntaxRegex                             = """\$\{([^\}]*)\}""".r
    final val InvalidCharactersForGetNameRegex           = """([^a-zA-Z_0-9\-\_\*])""".r
	final val InvalidCharactersForInitializeGetNameRegex = """^([^a-zA-Z\_\*])""".r
    private val starIndexRegex                           = """[^\*]+""".r
    
    def findAllAndParse(string:String):Iterator[TemplateElement] = findAllIn(string).map(parse(_))
    
    def findIn(string:String):Option[String]   = GetSyntaxRegex.findFirstIn(string)
    def findAllIn(string:String):MatchIterator = GetSyntaxRegex.findAllIn(string)
    def findInvalidCharacterForInitializeGetName(getName:String):Option[String] = InvalidCharactersForInitializeGetNameRegex.findFirstMatchIn(getName).map(_.group(1))
    def findInvalidCharacterForGetName(getName:String) = InvalidCharactersForGetNameRegex.findFirstMatchIn(getName).map(_.group(1))

    /**
     * @param getSyntaxString - an string in get syntax format "${...}"
     */
    def validate(getSyntaxString:String)={
        val get = GetSyntaxRegex.findFirstMatchIn(getSyntaxString)
        if(get.isEmpty) throw new InvalidGetSyntaxException(s"Invalid get syntax in '$getSyntaxString'")
        get.get.group(1) match{
	        case emptyGet if(emptyGet.trim.isEmpty)                 => throw new InvalidGetSyntaxException("Invalid get syntax in '"+getSyntaxString+"'. A name is required between '{}'. Example: ${someVarName}")
	        case nameWithSpace if(nameWithSpace.trim.contains(" ")) => throw new InvalidGetSyntaxException(s"Empty space not allowed in '$getSyntaxString'")
            case star if star.trim.startsWith("*") => {
                try {
        			starIndexRegex.findFirstIn(star.trim).getOrElse("1").toInt match{
        			    case index if index <= 0 => throw new InvalidStarIndexException(s"The star's index must be greater than 0. Please fix it in '$getSyntaxString'.")
        			    case _ =>
        			}
                }
                catch{
                    case nan: NumberFormatException => throw new InvalidStarIndexException(s"Only numbers can be used to access star's index. Please fix '$star' in '$getSyntaxString')")
                }
            }
            case other => {
            	findInvalidCharacterForGetName(other.trim).map(invalidChar=>throw new InvalidVariableNameException(s"The variable name must have only letters (without signs or spaces), numbers and symbols '-' and '_'. Invalid character '$invalidChar' in '$other'"))
                findInvalidCharacterForInitializeGetName(other.trim).map(invalidChar=>throw new InvalidVariableNameException(s"The variable name must start with a letter or an underscore ('_'). Invalid character '$invalidChar' in '$other'"))
            }
        }
    }
    
    /**
     * ${} 		   => exception
     * ${*}		   => Star(1)
     * ${*i}	   => Star(i) // 'i' as integer > 0
     * ${someName} => Get(someName)
     */
    def parse(getSyntaxString:String):TemplateElement = {
        validate(getSyntaxString)
        GetSyntaxRegex.findFirstMatchIn(getSyntaxString).get.group(1) match{
            case star if star.trim.startsWith("*") => Star(starIndexRegex.findFirstIn(star.trim).getOrElse("1").toInt)
            case name => Get(name.trim)
        }
    }
}

object KeyValueUtil {
    final val InvalidCharactersForKeyNameRegex           = """([^a-zA-Z_0-9\-\_])""".r
	final val InvalidCharactersForInitializeKeyNameRegex = """^([^a-zA-Z\_])""".r
    final val KeyRegex                                   = """\s*([^\s].*?)\s*=""".r
    final val ValueRegex                                 = """=(.*)""".r
    final val AttributionSignRegex                       = """(=){1}""".r
    
    def validate(keyValue:String) = KeyValueValidator.validateKeyValue(keyValue)
    def findKey(keyValue:String):Option[String]   = KeyRegex  .findFirstMatchIn(keyValue).map(_.group(1))
    def findValue(keyValue:String):Option[String] = ValueRegex.findFirstMatchIn(keyValue).map(_.group(1))
    
}

object KeyValueValidator {
    
    def validateKeyValue(keyValue:String):Unit={
        val attributionOption = KeyValueUtil.AttributionSignRegex.findAllMatchIn(keyValue)
        val keyOption         = KeyValueUtil.findKey(keyValue:String)
        val valueOption       = KeyValueUtil.findValue(keyValue)
        
        if(attributionOption.isEmpty) throw new NoAttributionSignException(s"No equal sign ('=') found in attribution: '$keyValue'.")
        if(attributionOption.size>1 ) throw new MoreThanOneAttributionSignException(s"The equal sign ('=') must be used only once per line. Please, break '$keyValue' into more than one line.") 
        
        if(keyOption.isEmpty) throw new NoVariableNameException(s"A variable name is required by left hand side of '=' in '$keyValue'. Example: someVarName$keyValue")        
        validateKey(keyOption.get)
        validateValue(valueOption.get)
    }
    
    def findInvalidCharacterForInitializeKeyName(key:String):Option[String] = KeyValueUtil.InvalidCharactersForInitializeKeyNameRegex.findFirstMatchIn(key).map(_.group(1))
    def findInvalidCharacterForName(key:String)=KeyValueUtil.InvalidCharactersForKeyNameRegex.findFirstMatchIn(key).map(_.group(1))
    
    def validateKey(key:String):Unit={
    	if(key.trim.isEmpty) throw new NoVariableNameException("A variable name is required by left hand side of '='. Example: name = ...")
    	findInvalidCharacterForInitializeKeyName(key).map(invalidChar=>throw new InvalidVariableNameException(s"The variable name must start with a letter or an underscore ('_'). Invalid character '$invalidChar' in '$key'"))
    	findInvalidCharacterForName(key).map(invalidChar=>throw new InvalidVariableNameException(s"The variable name must have only letters (without signs or spaces), numbers and symbols '-' and '_'. Invalid character '$invalidChar' in '$key'"))
    }
    
    def validateValue(value:String):Unit={
        GetUtil.findAllIn(value).foreach(get=>GetUtil.validate(get))
    }
}

class NoAttributionSignException(cause:String) extends Exception(cause)
class MoreThanOneAttributionSignException(cause:String) extends Exception(cause)
class NoVariableNameException(cause:String) extends Exception(cause)
class InvalidVariableNameException(cause:String) extends Exception(cause)
class NoValueContentException(cause:String) extends Exception(cause)
class InvalidStarIndexException(cause:String) extends Exception(cause)
class InvalidGetSyntaxException(cause:String) extends Exception(cause)
