package com.onyxdevtools.ai

import com.onyxdevtools.ai.transformation.BPETokenizer
import com.onyxdevtools.ai.transformation.Vocabulary
import kotlin.test.Test
import com.onyxdevtools.ai.layer.impl.*
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.test.assertTrue

class LLMTrainingTest {

    @Test
    fun testLargerDataSet() {
        // Define or build vocabulary
        val vocabulary = buildVocabularyFromText(aliceText)
        val tokenizer = BPETokenizer(vocabulary)

        // Tokenize the entire text
        val tokens = tokenizer.tokenize(aliceText).map { vocabulary.getId(it) }

        // Parameters
        val seqLength = 16
        val stride = seqLength

        // Create input-target sequences
        val inputSequences = mutableListOf<DoubleArray>()
        val targetSequences = mutableListOf<DoubleArray>()

        // Only include sequences with enough tokens for both input and target
        for (i in 0 until tokens.size - seqLength step stride) {
            val inputSeq = tokens.subList(i, i + seqLength).map { it.toDouble() }.toDoubleArray()
            val targetEnd = (i + seqLength + 1).coerceAtMost(tokens.size)
            val targetSeq = tokens.subList(i + 1, targetEnd)
            if (targetSeq.size < seqLength) continue  // Skip partial targets
            inputSequences.add(inputSeq)
            targetSeq.forEach { targetToken ->
                val oneHot = DoubleArray(vocabulary.size) { 0.0 }
                oneHot[targetToken] = 1.0
                targetSequences.add(oneHot)
            }
        }

        println("Number of input sequences: ${inputSequences.size}")
        println("Expected prediction rows: ${inputSequences.size * seqLength}")
        println("Actual target rows: ${targetSequences.size}")

        // Ensure targetSequences has the correct number of vectors
        val batchSize = inputSequences.size
        require(targetSequences.size == batchSize * seqLength) {
            "Target sequences size (${targetSequences.size}) must equal batchSize * seqLength (${batchSize * seqLength})"
        }

        // Configure neural network
        val embeddingDim = 64
        val numHeads = 4
        val ffHiddenDim = 128
        val tokensPerSample = seqLength

        val layers = listOf(
            EmbeddingLayer(vocabulary.size, embeddingDim),
            PositionalEncodingLayer(tokensPerSample, embeddingDim),
            MultiHeadAttentionLayer(tokensPerSample, embeddingDim, numHeads),
            LayerNormalizationLayer(embeddingDim),
            DenseLayer(embeddingDim, ffHiddenDim, Activation.RELU),
            DenseLayer(ffHiddenDim, embeddingDim, Activation.LINEAR),
            LayerNormalizationLayer(embeddingDim),
            DenseLayer(embeddingDim, vocabulary.size, Activation.LINEAR)
        )

        var model = NeuralNetwork(layers, learningRate = 0.0005)

        // Compute initial loss
        val initialPredictions = model.predict(inputSequences.toTypedArray())
        val initialLoss = calculateCrossEntropyLoss(initialPredictions, targetSequences.toTypedArray())

        // Train the model
        try {
            model = model.train(
                trainingFeatures = inputSequences.toTypedArray(),
                trainingValues = targetSequences.toTypedArray(),
                maxEpochs = 5000,
                patience = 250,
                batchSize = inputSequences.size,
                lossFn = { net ->
                    val predictions = net.predict(inputSequences.toTypedArray())
                    calculateCrossEntropyLoss(predictions, targetSequences.toTypedArray())
                },
                tokensPerSample = seqLength
            )
            println("Training completed successfully")
        } catch (e: Exception) {
            println("Training failed with exception: ${e.message}")
            e.printStackTrace()
        }

        // Compute final loss
        val finalPredictions = model.predict(inputSequences.toTypedArray())
        val finalLoss = calculateCrossEntropyLoss(finalPredictions, targetSequences.toTypedArray())

        // Test assertions
        assertTrue(initialLoss.isFinite(), "Initial loss should be finite")
        assertTrue(finalLoss.isFinite(), "Final loss should be finite")

        println("Initial Loss: $initialLoss")
        println("Final Loss: $finalLoss")

        // Show sample predictions
        finalPredictions.withIndex().groupBy { it.index / seqLength }.forEach { (seqIdx, preds) ->
            if (seqIdx < 5) {
                val lastPred = preds.last().value
                val predictedToken = lastPred.indices.maxByOrNull { lastPred[it] } ?: 0
                val actualToken = targetSequences[seqIdx * seqLength + (seqLength - 1)].indices.indexOfFirst { it.toDouble() == 1.0 }
                println("Sequence $seqIdx last input token: ${inputSequences[seqIdx].last().toInt()} -> Predicted: $predictedToken, Actual: $actualToken")
            }
        }

        assertTrue(finalLoss < initialLoss, "Model training completed successfully with loss improvement")

        // Example generation
        val padId = vocabulary.getId("[PAD]")
        val prompt = "Who is Alice?"
        val promptTokens = tokenizer.tokenize(prompt).map { vocabulary.getId(it) }.toMutableList()
        val maxGenerate = 20
        for (i in 0 until maxGenerate) {
            val currentLength = promptTokens.size
            val inputList = if (currentLength >= seqLength) {
                promptTokens.takeLast(seqLength)
            } else {
                List(seqLength - currentLength) { padId } + promptTokens
            }
            val input = inputList.map { it.toDouble() }.toDoubleArray()
            val predictions = model.predict(arrayOf(input))
            val nextPosition = min(currentLength, seqLength) - 1
            val logits = predictions[nextPosition]
            val predictedId = logits.indices.maxByOrNull { logits[it] }!!
            promptTokens.add(predictedId)
            val predictedToken = vocabulary.getToken(predictedId) ?: "[UNK]"
            println("Generated token: $predictedToken")
        }
        val generatedText = promptTokens.map { vocabulary.getToken(it) ?: "[UNK]" }.joinToString(" ")
        println("Generated text: $generatedText")
    }

    @Test
    fun testSimpleLLMTraining() {
        // Define a small vocabulary
        val vocab = mutableMapOf<String, Int>()
        vocab["hello"] = 0
        vocab["world"] = 1
        vocab["test"] = 2
        vocab["data"] = 3
        vocab["iduno"] = 4
        val vocabulary = object : Vocabulary {
            override fun getId(token: String): Int = vocab.getOrPut(token) { vocab.size }
            override fun getToken(index: Int): String? = vocab.entries.find { it.value == index }?.key
            override fun findId(token: String): Int? = vocab[token]
            override val size: Int get() = vocab.size
        }
        val tokenizer = BPETokenizer(vocabulary)

        // Create training samples where each input is a single token predicting the next
        val sampleTexts = listOf("hello world", "test data", "hello test", "world data")
        val trainingInputs = mutableListOf<DoubleArray>()
        val trainingTargets = mutableListOf<DoubleArray>()

        sampleTexts.forEach { text ->
            val tokens = tokenizer.tokenize(text).map { vocabulary.getId(it) }
            for (i in 0 until tokens.size - 1) {
                trainingInputs.add(doubleArrayOf(tokens[i].toDouble()))
                val targetOneHot = DoubleArray(vocabulary.size) { 0.0 }
                targetOneHot[tokens[i + 1]] = 1.0
                trainingTargets.add(targetOneHot)
            }
        }

        val inputSequences = trainingInputs.toTypedArray()
        val targetSequences = trainingTargets.toTypedArray()

        // Configure neural network with tokensPerSample=1
        val embeddingDim = 8
        val numHeads = 2
        val ffHiddenDim = 32
        val tokensPerSample = 1

        val layers = listOf(
            EmbeddingLayer(vocabulary.size, embeddingDim),
            PositionalEncodingLayer(tokensPerSample, embeddingDim),
            MultiHeadAttentionLayer(tokensPerSample, embeddingDim, numHeads),
            LayerNormalizationLayer(embeddingDim),
            DenseLayer(embeddingDim, ffHiddenDim, Activation.RELU),
            DenseLayer(ffHiddenDim, embeddingDim, Activation.LINEAR),
            LayerNormalizationLayer(embeddingDim),
            DenseLayer(embeddingDim, vocabulary.size, Activation.LINEAR)
        )

        var model = NeuralNetwork(layers, learningRate = 0.001)

        // Compute initial loss
        val initialPredictions = model.predict(inputSequences)
        val initialLoss = calculateCrossEntropyLoss(initialPredictions, targetSequences)

        // Train the model
        try {
            model = model.train(
                trainingFeatures = inputSequences,
                trainingValues = targetSequences,
                maxEpochs = 50000,
                patience = 1000,
                batchSize = inputSequences.size, // Use full batch
                lossFn = { net ->
                    val predictions = net.predict(inputSequences)
                    calculateCrossEntropyLoss(predictions, targetSequences)
                }
            )
            println("Training completed successfully")
        } catch (e: Exception) {
            println("Training failed with exception: ${e.message}")
            e.printStackTrace()
        }

        // Compute final loss
        val finalPredictions = model.predict(inputSequences)
        val finalLoss = calculateCrossEntropyLoss(finalPredictions, targetSequences)

        // Test that the model can at least make some predictions and loss is finite
        assertTrue(initialLoss.isFinite(), "Initial loss should be finite")
        assertTrue(finalLoss.isFinite(), "Final loss should be finite")

        println("Initial Loss: $initialLoss")
        println("Final Loss: $finalLoss")

        // Show sample predictions
        inputSequences.forEachIndexed { idx, input ->
            val prediction = model.predict(arrayOf(input))[0]
            val predictedToken = prediction.indices.maxByOrNull { prediction[it] } ?: 0
            val actualToken = targetSequences[idx].indices.indexOfFirst { targetSequences[idx][it] == 1.0 }
            println("Input: ${input[0].toInt()} -> Predicted: $predictedToken, Actual: $actualToken")
        }

        // Test passes if loss improved
        assertTrue(finalLoss < initialLoss, "Model training completed successfully with loss improvement")
    }

    private fun calculateCrossEntropyLoss(predictions: Array<DoubleArray>, targets: Array<DoubleArray>): Double {
        var loss = 0.0
        val batchSize = targets.size

        for (batchIndex in 0 until batchSize) {
            val logits = predictions[batchIndex]
            val expSum = logits.sumOf { exp(it) }
            val probs = logits.map { exp(it) / expSum }.toDoubleArray()

            val targetIndex = targets[batchIndex].indexOfFirst { it == 1.0 }
            if (targetIndex >= 0 && targetIndex < probs.size) {
                loss -= ln(probs[targetIndex] + 1e-10)
            }
        }
        return loss / batchSize
    }
}

// Simple vocabulary builder - in real BPE, use proper merges and train on corpus
fun buildVocabularyFromText(text: String): Vocabulary {
    val words = text.split(Regex("\\s+")).toSet()
    val vocabMap = words.mapIndexed { index, word -> word to index }.toMap().toMutableMap()
    vocabMap["[PAD]"] = vocabMap.size
    return object : Vocabulary {
        override fun getId(token: String): Int = vocabMap.getOrPut(token) { vocabMap.size }
        override fun getToken(index: Int): String? = vocabMap.entries.find { it.value == index }?.key
        override fun findId(token: String): Int? = vocabMap[token]
        override val size: Int get() = vocabMap.size
    }
}

val aliceText = """
Alice was beginning to get very tired of sitting by her sister on the bank, and of having nothing to do: once or twice she had peeped into the book her sister was reading, but it had no pictures or conversations in it, “and what is the use of a book,” thought Alice “without pictures or conversations?”

So she was considering in her own mind (as well as she could, for the hot day made her feel very sleepy and stupid), whether the pleasure of making a daisy-chain would be worth the trouble of getting up and picking the daisies, when suddenly a White Rabbit with pink eyes ran close by her.

There was nothing so very remarkable in that; nor did Alice think it so very much out of the way to hear the Rabbit say to itself, “Oh dear! Oh dear! I shall be late!” (when she thought it over afterwards, it occurred to her that she ought to have wondered at this, but at the time it all seemed quite natural); but when the Rabbit actually took a watch out of its waistcoat-pocket, and looked at it, and then hurried on, Alice started to her feet, for it flashed across her mind that she had never before seen a rabbit with either a waistcoat-pocket, or a watch to take out of it, and burning with curiosity, she ran across the field after it, and fortunately was just in time to see it pop down a large rabbit-hole under the hedge.

In another moment down went Alice after it, never once considering how in the world she was to get out again.

The rabbit-hole went straight on like a tunnel for some way, and then dipped suddenly down, so suddenly that Alice had not a moment to think about stopping herself before she found herself falling down a very deep well.

Either the well was very deep, or she fell very slowly, for she had plenty of time as she went down to look about her and to wonder what was going to happen next. First, she tried to look down and make out what she was coming to, but it was too dark to see anything; then she looked at the sides of the well, and noticed that they were filled with cupboards and book-shelves; here and there she saw maps and pictures hung upon pegs. She took down a jar from one of the shelves as she passed; it was labelled “ORANGE MARMALADE”, but to her great disappointment it was empty: she did not like to drop the jar for fear of killing somebody underneath, so managed to put it into one of the cupboards as she fell past it.

“Well!” thought Alice to herself, “after such a fall as this, I shall think nothing of tumbling down stairs! How brave they’ll all think me at home! Why, I wouldn’t say anything about it, even if I fell off the top of the house!” (Which was very likely true.)

Down, down, down. Would the fall never come to an end? “I wonder how many miles I’ve fallen by this time?” she said aloud. “I must be getting somewhere near the centre of the earth. Let me see: that would be four thousand miles down, I think—” (for, you see, Alice had learnt several things of this sort in her lessons in the schoolroom, and though this was not a very good opportunity for showing off her knowledge, as there was no one to listen to her, still it was good practice to say it over) “—yes, that’s about the right distance—but then I wonder what Latitude or Longitude I’ve got to?” (Alice had no idea what Latitude was, or Longitude either, but thought they were nice grand words to say.)

Presently she began again. “I wonder if I shall fall right through the earth! How funny it’ll seem to come out among the people that walk with their heads downward! The Antipathies, I think—” (she was rather glad there was no one listening, this time, as it didn’t sound at all the right word) “—but I shall have to ask them what the name of the country is, you know. Please, Ma’am, is this New Zealand or Australia?” (and she tried to curtsey as she spoke—fancy curtseying as you’re falling through the air! Do you think you could manage it?) “And what an ignorant little girl she’ll think me for asking! No, it’ll never do to ask: perhaps I shall see it written up somewhere.”

Down, down, down. There was nothing else to do, so Alice soon began talking again. “Dinah’ll miss me very much to-night, I should think!” (Dinah was the cat.) “I hope they’ll remember her saucer of milk at tea-time. Dinah my dear! I wish you were down here with me! There are no mice in the air, I’m afraid, but you might catch a bat, and that’s very like a mouse, you know. But do cats eat bats, I wonder?” And here Alice began to get rather sleepy, and went on saying to herself, in a dreamy sort of way, “Do cats eat bats? Do cats eat bats?” and sometimes, “Do bats eat cats?” for, you see, as she couldn’t answer either question, it didn’t much matter which way she put it. She felt that she was dozing off, and had just begun to dream that she was walking hand in hand with Dinah, and saying to her very earnestly, “Now, Dinah, tell me the truth: did you ever eat a bat?” when suddenly, thump! thump! down she came upon a heap of sticks and dry leaves, and the fall was over.

Alice was not a bit hurt, and she jumped up on to her feet in a moment: she looked up, but it was all dark overhead; before her was another long passage, and the White Rabbit was still in sight, hurrying down it. There was not a moment to be lost: away went Alice like the wind, and was just in time to hear it say, as it turned a corner, “Oh my ears and whiskers, how late it’s getting!” She was close behind it when she turned the corner, but the Rabbit was no longer to be seen: she found herself in a long, low hall, which was lit up by a row of lamps hanging from the roof.

There were doors all round the hall, but they were all locked; and when Alice had been all the way down one side and up the other, trying every door, she walked sadly down the middle, wondering how she was ever to get out again.

Suddenly she came upon a little three-legged table, all made of solid glass; there was nothing on it except a tiny golden key, and Alice’s first thought was that it might belong to one of the doors of the hall; but, alas! either the locks were too large, or the key was too small, but at any rate it would not open any of them. However, on the second time round, she came upon a low curtain she had not noticed before, and behind it was a little door about fifteen inches high: she tried the little golden key in the lock, and to her great delight it fitted!

Alice opened the door and found that it led into a small passage, not much larger than a rat-hole: she knelt down and looked along the passage into the loveliest garden you ever saw. How she longed to get out of that dark hall, and wander about among those beds of bright flowers and those cool fountains, but she could not even get her head through the doorway; “and even if my head would go through,” thought poor Alice, “it would be of very little use without my shoulders. Oh, how I wish I could shut up like a telescope! I think I could, if I only knew how to begin.” For, you see, so many out-of-the-way things had happened lately, that Alice had begun to think that very few things indeed were really impossible.

There seemed to be no use in waiting by the little door, so she went back to the table, half hoping she might find another key on it, or at any rate a book of rules for shutting people up like telescopes: this time she found a little bottle on it, (“which certainly was not here before,” said Alice,) and round the neck of the bottle was a paper label, with the words “DRINK ME,” beautifully printed on it in large letters.

It was all very well to say “Drink me,” but the wise little Alice was not going to do that in a hurry. “No, I’ll look first,” she said, “and see whether it’s marked ‘poison’ or not”; for she had read several nice little histories about children who had got burnt, and eaten up by wild beasts and other unpleasant things, all because they would not remember the simple rules their friends had taught them: such as, that a red-hot poker will burn you if you hold it too long; and that if you cut your finger very deeply with a knife, it usually bleeds; and she had never forgotten that, if you drink much from a bottle marked “poison,” it is almost certain to disagree with you, sooner or later.

However, this bottle was not marked “poison,” so Alice ventured to taste it, and finding it very nice, (it had, in fact, a sort of mixed flavour of cherry-tart, custard, pine-apple, roast turkey, toffee, and hot buttered toast,) she very soon finished it off.

*      *      *      *      *      *      *

    *      *      *      *      *      *

*      *      *      *      *      *      *

“What a curious feeling!” said Alice; “I must be shutting up like a telescope.”

And so it was indeed: she was now only ten inches high, and her face brightened up at the thought that she was now the right size for going through the little door into that lovely garden. First, however, she waited for a few minutes to see if she was going to shrink any further: she felt a little nervous about this; “for it might end, you know,” said Alice to herself, “in my going out altogether, like a candle. I wonder what I should be like then?” And she tried to fancy what the flame of a candle is like after the candle is blown out, for she could not remember ever having seen such a thing.

After a while, finding that nothing more happened, she decided on going into the garden at once; but, alas for poor Alice! when she got to the door, she found she had forgotten the little golden key, and when she went back to the table for it, she found she could not possibly reach it: she could see it quite plainly through the glass, and she tried her best to climb up one of the legs of the table, but it was too slippery; and when she had tired herself out with trying, the poor little thing sat down and cried.

“Come, there’s no use in crying like that!” said Alice to herself, rather sharply; “I advise you to leave off this minute!” She generally gave herself very good advice, (though she very seldom followed it), and sometimes she scolded herself so severely as to bring tears into her eyes; and once she remembered trying to box her own ears for having cheated herself in a game of croquet she was playing against herself, for this curious child was very fond of pretending to be two people. “But it’s no use now,” thought poor Alice, “to pretend to be two people! Why, there’s hardly enough of me left to make one respectable person!”

Soon her eye fell on a little glass box that was lying under the table: she opened it, and found in it a very small cake, on which the words “EAT ME” were beautifully marked in currants. “Well, I’ll eat it,” said Alice, “and if it makes me grow larger, I can reach the key; and if it makes me grow smaller, I can creep under the door; so either way I’ll get into the garden, and I don’t care which happens!”

She ate a little bit, and said anxiously to herself, “Which way? Which way?”, holding her hand on the top of her head to feel which way it was growing, and she was quite surprised to find that she remained the same size: to be sure, this generally happens when one eats cake, but Alice had got so much into the way of expecting nothing but out-of-the-way things to happen, that it seemed quite dull and stupid for life to go on in the common way.

So she set to work, and very soon finished off the cake.

CHAPTER II.
The Pool of Tears
“Curiouser and curiouser!” cried Alice (she was so much surprised, that for the moment she quite forgot how to speak good English); “now I’m opening out like the largest telescope that ever was! Good-bye, feet!” (for when she looked down at her feet, they seemed to be almost out of sight, they were getting so far off). “Oh, my poor little feet, I wonder who will put on your shoes and stockings for you now, dears? I’m sure I shan’t be able! I shall be a great deal too far off to trouble myself about you: you must manage the best way you can;—but I must be kind to them,” thought Alice, “or perhaps they won’t walk the way I want to go! Let me see: I’ll give them a new pair of boots every Christmas.”

And she went on planning to herself how she would manage it. “They must go by the carrier,” she thought; “and how funny it’ll seem, sending presents to one’s own feet! And how odd the directions will look!

     Alice’s Right Foot, Esq.,
       Hearthrug,
         near the Fender,
           (with Alice’s love).
Oh dear, what nonsense I’m talking!”

Just then her head struck against the roof of the hall: in fact she was now more than nine feet high, and she at once took up the little golden key and hurried off to the garden door.

Poor Alice! It was as much as she could do, lying down on one side, to look through into the garden with one eye; but to get through was more hopeless than ever: she sat down and began to cry again.

“You ought to be ashamed of yourself,” said Alice, “a great girl like you,” (she might well say this), “to go on crying in this way! Stop this moment, I tell you!” But she went on all the same, shedding gallons of tears, until there was a large pool all round her, about four inches deep and reaching half down the hall.

After a time she heard a little pattering of feet in the distance, and she hastily dried her eyes to see what was coming. It was the White Rabbit returning, splendidly dressed, with a pair of white kid gloves in one hand and a large fan in the other: he came trotting along in a great hurry, muttering to himself as he came, “Oh! the Duchess, the Duchess! Oh! won’t she be savage if I’ve kept her waiting!” Alice felt so desperate that she was ready to ask help of any one; so, when the Rabbit came near her, she began, in a low, timid voice, “If you please, sir—” The Rabbit started violently, dropped the white kid gloves and the fan, and skurried away into the darkness as hard as he could go.

Alice took up the fan and gloves, and, as the hall was very hot, she kept fanning herself all the time she went on talking: “Dear, dear! How queer everything is to-day! And yesterday things went on just as usual. I wonder if I’ve been changed in the night? Let me think: was I the same when I got up this morning? I almost think I can remember feeling a little different. But if I’m not the same, the next question is, Who in the world am I? Ah, that’s the great puzzle!” And she began thinking over all the children she knew that were of the same age as herself, to see if she could have been changed for any of them.

“I’m sure I’m not Ada,” she said, “for her hair goes in such long ringlets, and mine doesn’t go in ringlets at all; and I’m sure I can’t be Mabel, for I know all sorts of things, and she, oh! she knows such a very little! Besides, she’s she, and I’m I, and—oh dear, how puzzling it all is! I’ll try if I know all the things I used to know. Let me see: four times five is twelve, and four times six is thirteen, and four times seven is—oh dear! I shall never get to twenty at that rate! However, the Multiplication Table doesn’t signify: let’s try Geography. London is the capital of Paris, and Paris is the capital of Rome, and Rome—no, that’s all wrong, I’m certain! I must have been changed for Mabel! I’ll try and say ‘How doth the little—’” and she crossed her hands on her lap as if she were saying lessons, and began to repeat it, but her voice sounded hoarse and strange, and the words did not come the same as they used to do:—

"""
