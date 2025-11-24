package com.fts.ttbros.documents

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fts.ttbros.databinding.FragmentPdfViewerBinding
import com.fts.ttbros.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PdfViewerFragment : Fragment() {

    private var _binding: FragmentPdfViewerBinding? = null
    private val binding get() = _binding!!
    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var filePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filePath = arguments?.getString("filePath")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPdfViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (filePath != null) {
            openPdf(filePath!!)
        } else {
            binding.errorTextView.visibility = View.VISIBLE
            binding.errorTextView.text = "No file path provided"
        }
    }

    private fun openPdf(path: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = File(path)
                if (!file.exists()) {
                    withContext(Dispatchers.Main) {
                        binding.errorTextView.visibility = View.VISIBLE
                        binding.errorTextView.text = "File not found"
                    }
                    return@launch
                }

                fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                pdfRenderer = PdfRenderer(fileDescriptor!!)

                withContext(Dispatchers.Main) {
                    if (pdfRenderer != null) {
                        binding.pdfRecyclerView.layoutManager = LinearLayoutManager(requireContext())
                        binding.pdfRecyclerView.adapter = PdfPageAdapter(pdfRenderer!!)
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.errorTextView.visibility = View.VISIBLE
                    binding.errorTextView.text = "Error opening PDF: ${e.message}"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            pdfRenderer?.close()
            fileDescriptor?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _binding = null
    }

    inner class PdfPageAdapter(private val renderer: PdfRenderer) : RecyclerView.Adapter<PdfPageAdapter.PageViewHolder>() {

        inner class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.pageImageView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pdf_page, parent, false)
            return PageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            holder.imageView.setImageBitmap(null) // Clear previous image
            
            // Render asynchronously
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                try {
                    // Check if renderer is still open
                    if (renderer.pageCount <= position) return@launch
                    
                    val page = synchronized(renderer) {
                        renderer.openPage(position)
                    }
                    
                    val width = resources.displayMetrics.widthPixels
                    val scale = width.toFloat() / page.width
                    val height = (page.height * scale).toInt()
                    
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    
                    withContext(Dispatchers.Main) {
                        holder.imageView.setImageBitmap(bitmap)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        override fun getItemCount(): Int = renderer.pageCount
    }
}
