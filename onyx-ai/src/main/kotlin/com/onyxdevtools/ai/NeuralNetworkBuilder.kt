package com.onyxdevtools.ai

import Activation
import com.onyxdevtools.ai.layer.Layer
import com.onyxdevtools.ai.layer.impl.*
import com.onyxdevtools.ai.transformation.ColumnTransform
import com.onyxdevtools.ai.transformation.impl.*

/**
 * A builder-style DSL for constructing a [NeuralNetwork].
 * Provides a fluent API to configure network layers, transformations, and hyperparameters.
 *
 * **Usage:**
 * ```kotlin
 * val model = neuralNetwork {
 * // Configure hyperparameters
 * learningRate = 0.001
 * lambda = 0.0001 // L2 Regularization strength
 * beta1 = 0.9     // Adam optimizer beta1
 * beta2 = 0.999   // Adam optimizer beta2
 *
 * // Define the network architecture
 * layers {
 * dense(inputSize = 10, outputSize = 64, activation = Activation.RELU)
 * batchNorm(size = 64)
 * dense(inputSize = 64, outputSize = 1, activation = Activation.LINEAR)
 * }
 *
 * // Define preprocessing steps for input features
 * features {
 * log()      // Apply logarithmic transformation
 * meanStd()  // Apply mean/standard deviation normalization
 * // default() // Can be used as a placeholder or no-op
 * }
 *
 * // Define preprocessing/postprocessing steps for target values
 * values {
 * meanStd()  // Apply mean/standard deviation normalization
 * // default() // Can be used as a placeholder or no-op
 * }
 * }
 * ```
 * @property learningRate The step size for gradient descent optimization (Adam). Defaults to `1e-3`.
 * @property lambda The L2 regularization strength to prevent overfitting. Defaults to `1e-4`.
 * @property beta1 The exponential decay rate for the first moment estimates in the Adam optimizer. Defaults to `0.9`.
 * @property beta2 The exponential decay rate for the second moment estimates in the Adam optimizer. Defaults to `0.999`.
 */
class NeuralNetworkBuilder {
    private val layerList = mutableListOf<Layer>()
    private val featureTransformList = mutableListOf<ColumnTransform>()
    private val valueTransformList = mutableListOf<ColumnTransform>()

    /** The learning rate used during training by the optimizer (e.g., Adam). */
    var learningRate: Float = 1e-3f
    /** The L2 regularization parameter (lambda) to penalize large weights. */
    var lambda: Float = 1e-4f
    /** The exponential decay rate for the first moment estimates (Adam optimizer parameter). */
    private var beta1: Float = 0.9f
    /** The exponential decay rate for the second moment estimates (Adam optimizer parameter). */
    private var beta2: Float = 0.999f

    /**
     * Configures the layers of the neural network using the [LayerBuilder] DSL.
     * @param block A lambda function with [LayerBuilder] as its receiver to define the layers.
     */
    fun layers(block: LayerBuilder.() -> Unit) {
        layerList.addAll(LayerBuilder().apply(block).build())
    }

    /**
     * Configures the transformations to be applied to the input features before feeding them into the network.
     * Uses the [TransformBuilder] DSL.
     * @param block A lambda function with [TransformBuilder] as its receiver to define feature transformations.
     */
    fun features(block: TransformBuilder.() -> Unit) {
        featureTransformList.addAll(TransformBuilder().apply(block).build())
    }

    /**
     * Configures the transformations to be applied to the target values.
     * These transformations are also used in reverse during prediction to get the output in the original scale.
     * Uses the [TransformBuilder] DSL.
     * @param block A lambda function with [TransformBuilder] as its receiver to define value transformations.
     */
    fun values(block: TransformBuilder.() -> Unit) {
        valueTransformList.addAll(TransformBuilder().apply(block).build())
    }

    /**
     * Constructs and returns the configured [NeuralNetwork] instance.
     * @return A [NeuralNetwork] object initialized with the parameters and components defined in this builder.
     */
    fun build(): NeuralNetwork = NeuralNetwork(
        layers = layerList,
        featureTransforms = featureTransformList,
        valueTransforms = valueTransformList,
        learningRate = learningRate,
        lambda = lambda,
        beta1 = beta1,
        beta2 = beta2
    )
}

/**
 * Top-level builder function that provides a convenient entry point for creating a [NeuralNetwork]
 * using the [NeuralNetworkBuilder] DSL.
 *
 * @param block A lambda function with [NeuralNetworkBuilder] as its receiver.
 * @return The constructed [NeuralNetwork] instance.
 */
fun neuralNetwork(block: NeuralNetworkBuilder.() -> Unit): NeuralNetwork {
    return NeuralNetworkBuilder().apply(block).build()
}

/**
 * A builder specifically for defining the sequence of layers within a [NeuralNetwork].
 * Used within the `layers` block of the [NeuralNetworkBuilder] DSL.
 */
class LayerBuilder {
    private val layers = mutableListOf<Layer>()

    /**
     * Adds a fully connected (dense) layer to the network architecture.
     *
     * @param inputSize The number of input neurons (features) for this layer.
     * @param outputSize The number of output neurons for this layer.
     * @param activation The [Activation] function to apply to the layer's output.
     * @param dropoutRate The probability of dropping out neurons during training (regularization). Defaults to `0.0` (no dropout).
     */
    fun dense(inputSize: Int, outputSize: Int, activation: Activation, dropoutRate: Float = 0.0f) {
        layers += DenseLayer(inputSize, outputSize, activation, dropoutRate)
    }

    /**
     * Adds a batch normalization layer to the network architecture.
     * Helps stabilize learning and can act as a regularizer.
     *
     * @param size The number of features/neurons the normalization is applied to (should match the output size of the previous layer).
     */
    fun batchNorm(size: Int) {
        layers += BatchNormalizationLayer(size)
    }

    /**
     * Adds an embedding layer to the network architecture.
     * Maps discrete input tokens to dense vectors of fixed size.
     *
     * @param vocabSize The size of the vocabulary (number of unique input tokens).
     * @param embeddingSize The dimensionality of the embedding vectors.
     */
    fun embedding(vocabSize: Int, embeddingSize: Int) {
        layers += EmbeddingLayer(vocabSize, embeddingSize)
    }

    /**
     * Adds a layer normalization layer to the network architecture.
     * Normalizes inputs across the feature dimension independently for each sample.
     *
     * @param size The number of features to normalize.
     */
    fun layerNorm(size: Int) {
        layers += LayerNormalizationLayer(size)
    }

    /**
     * Adds a multi-head attention layer to the network architecture.
     * Allows the model to jointly attend to information from different representation subspaces.
     *
     * @param tokensPerSample The fixed number of tokens per input sample.
     * @param modelSize The dimensionality of each token's embedding.
     * @param headCount The number of attention heads to use.
     */
    fun multiHeadAttention(tokensPerSample: Int, modelSize: Int, headCount: Int) {
        layers += CachedMultiHeadAttentionLayer(tokensPerSample, modelSize, headCount)
    }

    /**
     * Adds a positional encoding layer to the network architecture.
     * Injects information about the position of tokens into their embeddings using sine and cosine functions.
     *
     * @param tokensPerSample The fixed length of input sequences.
     * @param embeddingSize The dimensionality of the embeddings.
     */
    fun positionalEncoding(tokensPerSample: Int, embeddingSize: Int) {
        layers += PositionalEncodingLayer(tokensPerSample, embeddingSize)
    }

    /**
     * Returns the list of configured [Layer] objects.
     * Called internally by [NeuralNetworkBuilder].
     * @return A list of [Layer] instances defined within this builder.
     */
    fun build(): List<Layer> = layers
}

/**
 * A builder for defining a sequence of data transformations ([ColumnTransform]).
 * Used within the `features` and `values` blocks of the [NeuralNetworkBuilder] DSL
 * to specify preprocessing steps.
 */
@Suppress("unused")
class TransformBuilder {
    private val transforms = mutableListOf<ColumnTransform>()

    /**
     * Adds a [BooleanTransform] to the sequence.
     * Typically converts boolean features into numerical representations (e.g., 0 or 1).
     */
    fun boolean() {
        transforms += BooleanTransform()
    }

    /**
     * Adds a [LogTransform] to the sequence.
     * Applies a natural logarithm transformation, often useful for skewed data.
     */
    fun log() {
        transforms += LogTransform()
    }

    /**
     * Adds a [MeanStdNormalizer] to the sequence.
     * Standardizes the data by subtracting the mean and dividing by the standard deviation.
     */
    fun meanStd() {
        transforms += MeanStdNormalizer()
    }

    /**
     * Adds a [TimeDecayTransform] to the sequence.
     * Applies a time-based decay factor, useful for time-series data where recent data might be more relevant.
     * @param lambda The decay rate parameter.
     */
    fun timeDecay(lambda: Float) {
        transforms += TimeDecayTransform(lambda)
    }

    /**
     * Adds a [DefaultTransform] to the sequence.
     * Acts as an identity transformation (no-op), useful as a placeholder or default.
     */
    fun default() {
        transforms += DefaultTransform()
    }

    /**
     * Adds a [CategoricalIndexer] to the sequence.
     * Converts categorical features (strings or other types) into numerical indices.
     */
    fun categorical() {
        transforms += CategoricalIndexer()
    }

    /**
     * Adds a [ColumnL2Normalizer] to the sequence.
     * Normalizes the feature vector for each column to have a unit L2 norm.
     */
    fun l2Normalization() {
        transforms += ColumnL2Normalizer()
    }

    /**
     * Adds a [RobustScaler] to the sequence.
     * Scales features using statistics that are robust to outliers, typically the median and interquartile range (IQR).
     */
    fun robustScaler() {
        transforms += RobustScaler()
    }

    /**
     * Adds a [QuantileTransformer] to the sequence.
     * Transforms features based on quantile information, often mapping the data to a uniform or normal distribution.
     */
    fun quantile() {
        transforms += QuantileTransformer()
    }

    /**
     * Adds a [MinMaxScaler] to the sequence.
     * Scales features to a specific range, commonly [0, 1].
     */
    fun minMaxScaler() {
        transforms += MinMaxScaler()
    }

    /**
     * Adds a [MaxAbsScaler] to the sequence.
     * Scales each feature by its maximum absolute value, preserving the original sign and potential sparsity.
     */
    fun maxAbsScaler() {
        transforms += MaxAbsScaler()
    }

    /**
     * Adds a custom pipeline of [ColumnTransform]s defined by the builder lambda.
     * This allows for combining multiple transformations sequentially.
     * @param block A lambda function that returns a list of [ColumnTransform]s to be applied as a single step.
     */
    fun columnTransform(block: TransformBuilder.() -> Unit) {
        transforms += ColumnTransformPipeline(
            TransformBuilder().apply(block).build()
        )
    }

    /**
     * Returns the list of configured [ColumnTransform] objects.
     * Called internally by [NeuralNetworkBuilder].
     * @return A list of [ColumnTransform] instances defined within this builder.
     */
    fun build(): List<ColumnTransform> = transforms
}